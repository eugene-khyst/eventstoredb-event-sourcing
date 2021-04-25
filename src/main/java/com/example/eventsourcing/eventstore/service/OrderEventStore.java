package com.example.eventsourcing.eventstore.service;

import static com.eventstore.dbclient.ExpectedRevision.NO_STREAM;
import static com.eventstore.dbclient.ExpectedRevision.expectedRevision;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.ConsumerStrategy;
import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient;
import com.eventstore.dbclient.PersistentSubscription;
import com.eventstore.dbclient.PersistentSubscriptionListener;
import com.eventstore.dbclient.PersistentSubscriptionSettings;
import com.eventstore.dbclient.ReadResult;
import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import com.eventstore.dbclient.StreamNotFoundException;
import com.eventstore.dbclient.SubscribePersistentSubscriptionOptions;
import com.eventstore.dbclient.WrongExpectedVersionException;
import com.example.eventsourcing.eventstore.config.EventStoreDbProperties;
import com.example.eventsourcing.eventstore.eventsourcing.Event;
import com.example.eventsourcing.eventstore.eventsourcing.OptimisticConcurrencyControlError;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventStore {

  private static final String ORDER_BY_CATEGORY_STREAM_NAME = "$ce-order";
  private static final String ORDER_STREAM_NAME_PREFIX = "order-";

  private final EventStoreDbProperties properties;
  private final EventJsonSerde jsonSerde;
  private final EventStoreDBClient client;
  private final EventStoreDBPersistentSubscriptionsClient persistentSubscriptionsClient;

  @SneakyThrows
  public long append(Event event, long expectedRevision) {
    Objects.requireNonNull(event);
    log.debug("Appending event with expected revision {} {}", expectedRevision, event);
    EventData eventData =
        EventData.builderAsBinary(event.getEventType(), jsonSerde.serialize(event)).build();
    AppendToStreamOptions options =
        AppendToStreamOptions.get()
            .expectedRevision(
                expectedRevision >= 0 ? expectedRevision(expectedRevision) : NO_STREAM);
    try {
      return client
          .appendToStream(toStreamName(event.getAggregateId()), options, eventData)
          .get()
          .getNextExpectedRevision()
          .getValueUnsigned();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof WrongExpectedVersionException) {
        WrongExpectedVersionException innerException = (WrongExpectedVersionException) e.getCause();
        long actualRevision = innerException.getActualVersion().getValueUnsigned();
        log.debug(
            "Optimistic concurrency control error in aggregate {}: actual revision is {} but expected {}",
            event.getAggregateId(),
            actualRevision,
            expectedRevision);
        throw new OptimisticConcurrencyControlError(actualRevision, expectedRevision);
      }
      throw e;
    }
  }

  @SneakyThrows
  public List<Event> readEvents(UUID aggregateId) {
    Objects.requireNonNull(aggregateId);
    log.debug("Reading events for aggregate {}", aggregateId);
    List<ResolvedEvent> resolvedEvents;
    try {
      ReadResult result = client.readStream(toStreamName(aggregateId)).get();
      resolvedEvents = result.getEvents();
    } catch (ExecutionException e) {
      Throwable innerException = e.getCause();
      if (innerException instanceof StreamNotFoundException) {
        log.debug("No events for aggregate {}", aggregateId);
        return Collections.emptyList();
      }
      throw e;
    }
    List<Event> events = new ArrayList<>();
    for (ResolvedEvent resolvedEvent : resolvedEvents) {
      Event event = toEvent(resolvedEvent.getOriginalEvent());
      events.add(event);
    }
    return events;
  }

  @SneakyThrows
  public void subscribe(Consumer<Event> consumer) {
    Objects.requireNonNull(consumer);
    log.debug(
        "Creating persistent subscription on {} with consumer {}",
        ORDER_BY_CATEGORY_STREAM_NAME,
        consumer);

    try {
      PersistentSubscriptionSettings settings =
          PersistentSubscriptionSettings.builder()
              .enableLinkResolution()
              .consumerStrategy(ConsumerStrategy.Pinned)
              .build();

      persistentSubscriptionsClient
          .create(
              ORDER_BY_CATEGORY_STREAM_NAME,
              properties.getPersistentSubscription().getGroup(),
              settings)
          .get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof StatusRuntimeException) {
        StatusRuntimeException innerException = (StatusRuntimeException) e.getCause();
        if (innerException.getStatus().getCode() == Status.ALREADY_EXISTS.getCode()) {
          log.info(innerException.getMessage());
        } else {
          throw e;
        }
      }
    }

    SubscribePersistentSubscriptionOptions subscriptionOptions =
        SubscribePersistentSubscriptionOptions.get()
            .setBufferSize(properties.getPersistentSubscription().getBufferSize());

    persistentSubscriptionsClient
        .subscribe(
            ORDER_BY_CATEGORY_STREAM_NAME,
            properties.getPersistentSubscription().getGroup(),
            subscriptionOptions,
            new PersistentSubscriptionListener() {
              @Override
              public void onEvent(
                  PersistentSubscription subscription, ResolvedEvent resolvedEvent) {
                RecordedEvent recordedEvent = resolvedEvent.getEvent();
                log.debug(
                    "Received event {}@{} from subscription {}",
                    recordedEvent.getStreamId(),
                    recordedEvent.getStreamRevision().getValueUnsigned(),
                    subscription.getSubscriptionId());
                try {
                  Event event = toEvent(recordedEvent);
                  consumer.accept(event);
                  subscription.ack(resolvedEvent);
                } catch (Exception e) {
                  log.error(
                      String.format(
                          "Error processing event %s@%s from subscription %s: %s",
                          recordedEvent.getStreamId(),
                          recordedEvent.getStreamRevision().getValueUnsigned(),
                          subscription.getSubscriptionId(),
                          e.getMessage()),
                      e);
                }
              }
            })
        .get();
  }

  private Event toEvent(RecordedEvent recordedEvent) {
    String eventType = recordedEvent.getEventType();
    long revision = recordedEvent.getStreamRevision().getValueUnsigned();
    Event event = jsonSerde.deserialize(recordedEvent.getEventData(), eventType);
    event.setRevision(revision);
    return event;
  }

  private String toStreamName(UUID aggregateId) {
    return ORDER_STREAM_NAME_PREFIX + aggregateId;
  }
}

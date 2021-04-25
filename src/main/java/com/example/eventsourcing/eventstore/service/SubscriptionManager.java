package com.example.eventsourcing.eventstore.service;

import com.example.eventsourcing.eventstore.config.EventStoreDbProperties;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionManager {

  private final EventStoreDbProperties properties;
  private final OrderEventStore eventStore;
  private final OrderEventHandler eventHandler;

  @PostConstruct
  public void init() {
    if (properties.isAutoSubscribe()) {
      log.debug("Creating subscriptions");
      eventStore.subscribe(eventHandler::process);
    } else {
      log.info("Skipping creating subscriptions autoSubscribe=false");
    }
  }
}

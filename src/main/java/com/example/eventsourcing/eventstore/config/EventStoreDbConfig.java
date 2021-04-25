package com.example.eventsourcing.eventstore.config;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class EventStoreDbConfig {

  private final EventStoreDbProperties properties;

  @Bean
  public EventStoreDBClient eventStoreDBClient() {
    EventStoreDBClientSettings settings =
        EventStoreDBConnectionString.parseOrThrow(properties.getConnectionString());
    return EventStoreDBClient.create(settings);
  }

  @Bean
  public EventStoreDBPersistentSubscriptionsClient persistentSubscriptionsClient() {
    EventStoreDBClientSettings settings =
        EventStoreDBConnectionString.parseOrThrow(properties.getConnectionString());
    return EventStoreDBPersistentSubscriptionsClient.create(settings);
  }
}

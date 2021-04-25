package com.example.eventsourcing.eventstore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("eventstoredb")
@Data
public class EventStoreDbProperties {

  @Data
  public static class PersistentSubscription {
    private String group;
    private int bufferSize;
  }

  private String connectionString;
  private boolean autoSubscribe = true;
  private PersistentSubscription persistentSubscription = new PersistentSubscription();
}

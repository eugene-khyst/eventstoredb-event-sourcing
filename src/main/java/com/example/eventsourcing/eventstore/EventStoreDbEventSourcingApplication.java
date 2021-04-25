package com.example.eventsourcing.eventstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventStoreDbEventSourcingApplication {

  public static void main(String[] args) {
    SpringApplication.run(EventStoreDbEventSourcingApplication.class, args);
  }
}

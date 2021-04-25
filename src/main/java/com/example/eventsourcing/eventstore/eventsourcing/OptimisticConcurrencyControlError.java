package com.example.eventsourcing.eventstore.eventsourcing;

public class OptimisticConcurrencyControlError extends Error {

  public OptimisticConcurrencyControlError(long actualRevision, long expectedRevision) {
    super(
        String.format(
            "Actual revision %s doesn't match expected revision %s",
            actualRevision, expectedRevision));
  }
}

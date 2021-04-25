package com.example.eventsourcing.eventstore.eventsourcing;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class Event {

  protected UUID aggregateId;
  protected long revision;
  protected Instant createdDate = Instant.now();

  public Event(UUID aggregateId) {
    this.aggregateId = aggregateId;
  }

  public String getEventType() {
    return this.getClass().getSimpleName();
  }
}

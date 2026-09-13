package io.eventdriven.eventsversioning.snapshottesting;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.eventdriven.eventsversioning.serialization.Serializer;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.scrubbers.DateScrubber;
import org.approvaltests.scrubbers.Scrubbers;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class EventsSnapshotTests {
  record ShoppingCartConfirmed(
    UUID shoppingCartId,
    String clientId,
    OffsetDateTime confirmedAt
  ) {}

  @Test
  public void shoppingCartConfirmed_WithScrubbers_IsCompatible() throws JsonProcessingException {
    var event = new ShoppingCartConfirmed(UUID.randomUUID(), "anonymised", OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    var options = new Options(Scrubbers.scrubAll(
      Scrubbers::scrubGuid,
      DateScrubber.getScrubberFor("2024-01-01T12:00:00Z")
    ));
    Approvals.verify(Serializer.mapper.writeValueAsString(event), options);
  }
}

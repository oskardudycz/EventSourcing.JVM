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
  private static final UUID FIXED_CART_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final OffsetDateTime FIXED_DATE = OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  record ShoppingCartConfirmed(
    UUID shoppingCartId,
    String clientId,
    OffsetDateTime confirmedAt
  ) {}

  @Test
  public void shoppingCartConfirmed_WithCompleteData_IsCompatible() throws JsonProcessingException {
    var event = new ShoppingCartConfirmed(FIXED_CART_ID, "anonymised", FIXED_DATE);
    Approvals.verify(Serializer.mapper.writeValueAsString(event));
  }

  @Test
  public void shoppingCartConfirmed_WithOnlyRequiredData_IsCompatible() throws JsonProcessingException {
    var event = new ShoppingCartConfirmed(FIXED_CART_ID, null, FIXED_DATE);
    Approvals.verify(Serializer.mapper.writeValueAsString(event));
  }

  @Test
  public void shoppingCartConfirmed_WithScrubbers_IsCompatible() throws JsonProcessingException {
    var event = new ShoppingCartConfirmed(UUID.randomUUID(), "anonymised", OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS));
    var options = new Options(Scrubbers.scrubAll(
      Scrubbers::scrubGuid,
      DateScrubber.getScrubberFor("2024-01-01T12:00:00.000Z")
    ));
    Approvals.verify(Serializer.mapper.writeValueAsString(event), options);
  }
}

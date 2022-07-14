package io.eventdriven.introductiontoeventsourcing.e06_business_logic.occurrent.immutable.solution2;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.introductiontoeventsourcing.e06_business_logic.occurrent.immutable.solution2.BusinessLogic.ShoppingCart.ShoppingCartStatus;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.occurrent.application.converter.CloudEventConverter;
import org.occurrent.application.converter.jackson.JacksonCloudEventConverter;
import org.occurrent.application.service.blocking.ApplicationService;
import org.occurrent.application.service.blocking.generic.GenericApplicationService;
import org.occurrent.dsl.query.blocking.DomainEventQueries;
import org.occurrent.eventstore.inmemory.InMemoryEventStore;

import java.net.URI;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic.occurrent.immutable.solution2.BusinessLogic.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.occurrent.application.composition.command.StreamCommandComposition.composeCommands;
import static org.occurrent.application.composition.command.partial.PartialFunctionApplication.partial;
import static org.occurrent.filter.Filter.streamId;

public class BusinessLogicTests {
  private DomainEventQueries<ShoppingCartEvent> eventQueries;

  private ApplicationService<ShoppingCartEvent> applicationService;

  @BeforeEach
  void setup() {
    var objectMapper = new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    var eventStore = new InMemoryEventStore();
    CloudEventConverter<ShoppingCartEvent> cloudEventConverter = new JacksonCloudEventConverter<>(objectMapper, URI.create("urn:eventsourcing:jvm:samples:occurrent"));
    eventQueries = new DomainEventQueries<>(eventStore, cloudEventConverter);
    applicationService = new GenericApplicationService<>(eventStore, cloudEventConverter);
  }


  @Test
  public void gettingState_ForSequenceOfEvents_ShouldSucceed() {
    var shoppingCartId = UUID.randomUUID();
    var clientId = UUID.randomUUID();
    var shoesId = UUID.randomUUID();
    var tShirtId = UUID.randomUUID();

    var twoPairsOfShoes = new ProductItem(shoesId, 2);
    var tShirt = new ProductItem(tShirtId, 1);

    var shoesPrice = 100;
    var tShirtPrice = 50;

    var pricedPairOfShoes = new PricedProductItem(shoesId, 1, shoesPrice);
    var pricedTShirt = new PricedProductItem(tShirtId, 1, tShirtPrice);

    String streamId = "shopping_cart-%s".formatted(shoppingCartId);

    applicationService.execute(streamId, compose(
      partial(BusinessLogic::open, shoppingCartId, clientId),
      partial(BusinessLogic::addProductItem, twoPairsOfShoes, FakeProductPriceCalculator.returning(shoesPrice)),
      partial(BusinessLogic::addProductItem, tShirt, FakeProductPriceCalculator.returning(tShirtPrice)),
      partial(BusinessLogic::removeProductItem, pricedPairOfShoes),
      BusinessLogic::confirm
    ));

    var shoppingCart = StreamEx.of(eventQueries.query(streamId(streamId))).foldLeft(ShoppingCart.empty(), ShoppingCart::when);

    assertAll(
      () -> assertThat(shoppingCart.id()).isEqualTo(shoppingCartId),
      () -> assertThat(shoppingCart.clientId()).isEqualTo(clientId),
      () -> assertThat(shoppingCart.status()).isEqualTo(ShoppingCartStatus.Confirmed),
      () -> assertThat(shoppingCart.productItems().values()).containsExactly(pricedPairOfShoes, pricedTShirt)
    );
  }

  /**
   * A function that transforms domain functions that take a ShoppingCart and returns a single ShoppingCartEvent
   * into functions that takes a <code>Stream&lt;ShoppingCartEvent&gt;</code> and returns a <code>Stream&lt;ShoppingCartEvent&gt;</code>
   * because this is what Occurrent expects.
   * <p>
   * You can make this function generic (and thus reusable for all use domains and use cases) if you wish,
   * but we keep it concrete and simple for now.
   */
  @SafeVarargs
  private static Function<Stream<ShoppingCartEvent>, Stream<ShoppingCartEvent>> compose(Function<ShoppingCart, ShoppingCartEvent>... domainMethods) {
    Stream<Function<Stream<ShoppingCartEvent>, Stream<ShoppingCartEvent>>> functionsToInvoke =
      Stream.of(domainMethods)
        .map(domainMethod -> events -> {
          ShoppingCart shoppingCart = StreamEx.of(events)
            .foldLeft(ShoppingCart.empty(), ShoppingCart::when);
          ShoppingCartEvent newEvent = domainMethod.apply(shoppingCart);
          return Stream.of(newEvent);
        });

    return composeCommands(functionsToInvoke);
  }
}

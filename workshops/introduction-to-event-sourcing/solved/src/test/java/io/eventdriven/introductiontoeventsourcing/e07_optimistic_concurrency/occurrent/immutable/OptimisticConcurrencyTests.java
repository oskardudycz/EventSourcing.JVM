package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.occurrent.immutable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cloudevents.CloudEvent;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.tools.EventStoreDBTest;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.occurrent.immutable.BusinessLogic.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.occurrent.immutable.BusinessLogic.PricedProductItem;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.occurrent.immutable.BusinessLogic.ProductItem;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.occurrent.immutable.BusinessLogic.ShoppingCart.ShoppingCartStatus;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.occurrent.immutable.BusinessLogic.ShoppingCartEvent;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.occurrent.application.converter.CloudEventConverter;
import org.occurrent.application.converter.jackson.JacksonCloudEventConverter;
import org.occurrent.application.service.blocking.ApplicationService;
import org.occurrent.dsl.query.blocking.DomainEventQueries;
import org.occurrent.eventstore.api.WriteCondition;
import org.occurrent.eventstore.api.WriteConditionNotFulfilledException;
import org.occurrent.eventstore.api.WriteResult;
import org.occurrent.eventstore.api.blocking.EventStore;
import org.occurrent.eventstore.api.blocking.EventStream;
import org.occurrent.eventstore.inmemory.InMemoryEventStore;

import java.net.URI;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.occurrent.immutable.BusinessLogic.ShoppingCart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.occurrent.application.composition.command.partial.PartialFunctionApplication.partial;
import static org.occurrent.eventstore.api.WriteCondition.streamVersionEq;
import static org.occurrent.filter.Filter.streamId;

public class OptimisticConcurrencyTests extends EventStoreDBTest {
  private DomainEventQueries<ShoppingCartEvent> eventQueries;

  private CustomApplicationService applicationService;

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
    applicationService = new CustomApplicationService(eventStore, cloudEventConverter);
  }

  @Test
  public void gettingState_ForSequenceOfEvents_ShouldSucceed() throws InterruptedException {
    var shoppingCartId = UUID.randomUUID();
    var clientId = UUID.randomUUID();
    var shoesId = UUID.randomUUID();
    var tShirtId = UUID.randomUUID();

    var twoPairsOfShoes = new ProductItem(shoesId, 2);
    var tShirt = new ProductItem(tShirtId, 1);

    var shoesPrice = 100;
    var tShirtPrice = 50;

    var pricedTwoPairOfShoes = new PricedProductItem(shoesId, 2, shoesPrice);

    String streamId = "shopping_cart-%s".formatted(shoppingCartId);

    // Open
    applicationService.execute(streamId, evolveThenCall(partial(BusinessLogic::open, shoppingCartId, clientId)));

    // Add two pairs of shoes
    applicationService.execute(streamId, evolveThenCall(partial(BusinessLogic::addProductItem, twoPairsOfShoes, FakeProductPriceCalculator.returning(shoesPrice))));

    // Add T-Shirt with wrong expected stream version
    Throwable throwable = catchThrowable(() -> applicationService.execute(streamId, __ -> WriteCondition.streamVersionEq(1), evolveThenCall(partial(BusinessLogic::addProductItem, tShirt, FakeProductPriceCalculator.returning(tShirtPrice)))));

    var shoppingCart = StreamEx.of(eventQueries.query(streamId(streamId))).foldLeft(ShoppingCart.empty(), ShoppingCart::when);

    assertAll(
      () -> assertThat(throwable).isExactlyInstanceOf(WriteConditionNotFulfilledException.class),
      () -> assertThat(shoppingCart.id()).isEqualTo(shoppingCartId),
      () -> assertThat(shoppingCart.clientId()).isEqualTo(clientId),
      () -> assertThat(shoppingCart.status()).isEqualTo(ShoppingCartStatus.Pending),
      () -> assertThat(shoppingCart.productItems().values()).containsExactly(pricedTwoPairOfShoes)
    );
  }


  private static Function<Stream<ShoppingCartEvent>, Stream<ShoppingCartEvent>> evolveThenCall(Function<ShoppingCart, ShoppingCartEvent> domainMethod) {
    return events -> {
      ShoppingCart shoppingCart = StreamEx.of(events)
        .foldLeft(ShoppingCart.empty(), ShoppingCart::when);
      ShoppingCartEvent newEvent = domainMethod.apply(shoppingCart);
      return Stream.of(newEvent);
    };
  }


  private record CustomApplicationService(EventStore eventStore,
                                          CloudEventConverter<ShoppingCartEvent> cloudEventConverter) implements ApplicationService<ShoppingCartEvent> {

    @Override
    public WriteResult execute(String streamId, Function<Stream<ShoppingCartEvent>, Stream<ShoppingCartEvent>> function,
                               Consumer<Stream<ShoppingCartEvent>> __) {
      return execute(streamId, cloudEvents -> streamVersionEq(cloudEvents.version()), function);
    }

    public WriteResult execute(String streamId, Function<EventStream<CloudEvent>, WriteCondition> writeConditionFunction, Function<Stream<ShoppingCartEvent>, Stream<ShoppingCartEvent>> function) {
      EventStream<CloudEvent> eventStream = eventStore.read(streamId);
      Stream<ShoppingCartEvent> eventsInStream = cloudEventConverter.toDomainEvents(eventStream.events());
      Stream<ShoppingCartEvent> newDomainEvents = function.apply(eventsInStream);
      Stream<CloudEvent> newEvents = cloudEventConverter.toCloudEvents(newDomainEvents);
      return this.eventStore.write(streamId, writeConditionFunction.apply(eventStream), newEvents);
    }
  }
}

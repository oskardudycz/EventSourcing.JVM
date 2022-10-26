package io.eventdriven.shoppingcarts;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.eventstore.dbclient.ParseError;
import io.eventdriven.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.shoppingcarts.productitems.ProductItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.eventdriven.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.shoppingcarts.ShoppingCart.*;

public class ShoppingCartTests {

  @Test
  public void test() {

  }

  private EventStoreDBClient eventStore;

  @BeforeEach
  void beforeEach() throws ParseError {
    var settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    this.eventStore = EventStoreDBClient.create(settings);
  }
}

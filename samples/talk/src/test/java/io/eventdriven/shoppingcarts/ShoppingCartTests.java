package io.eventdriven.shoppingcarts;

import io.kurrent.dbclient.ConnectionStringParsingException;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBConnectionString;
import io.eventdriven.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.TestDatabases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.eventdriven.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.shoppingcarts.ShoppingCart.*;

public class ShoppingCartTests {

  @Test
  public void test() {

  }

  private KurrentDBClient eventStore;

  @BeforeEach
  void beforeEach() throws ConnectionStringParsingException {
    var settings = KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString());
    this.eventStore = KurrentDBClient.create(settings);
  }
}

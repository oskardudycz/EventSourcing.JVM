package io.eventdriven.shoppingcarts;

import static io.eventdriven.shoppingcarts.ShoppingCart.*;
import static io.eventdriven.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.testing.TestDatabases;
import io.kurrent.dbclient.ConnectionStringParsingException;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBConnectionString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ShoppingCartTests {

    @Test
    public void test() {}

    private KurrentDBClient eventStore;

    @BeforeEach
    void beforeEach() throws ConnectionStringParsingException {
        var settings = KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString());
        this.eventStore = KurrentDBClient.create(settings);
    }
}

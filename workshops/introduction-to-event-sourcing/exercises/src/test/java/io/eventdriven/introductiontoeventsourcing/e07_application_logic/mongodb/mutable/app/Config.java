package io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app;

import static io.eventdriven.eventstores.mongodb.MongoDBEventStore.Storage;

import com.mongodb.client.MongoClient;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;
import io.eventdriven.eventstores.mongodb.config.MongoDBProperties;
import io.eventdriven.eventstores.mongodb.config.NativeMongoConfig;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.core.http.GlobalExceptionHandler;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.core.serializer.DefaultSerializer;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app.shoppingcarts.ShoppingCartStore;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app.shoppingcarts.productItems.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app.shoppingcarts.productItems.ProductPriceCalculator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.annotation.ApplicationScope;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(MongoDBProperties.class)
class Config {
    @Bean
    JsonMapper defaultJSONMapper() {
        return DefaultSerializer.mapper;
    }

    @Bean
    @Scope("singleton")
    MongoClient mongoDBClient(MongoDBProperties properties) {
        return NativeMongoConfig.createClient(properties.connectionString());
    }

    @Bean
    @Scope("singleton")
    MongoDBEventStore eventStore(MongoClient mongoClient) {
        return MongoDBEventStore.with(Storage.EventAsDocument, mongoClient, "e07_application_logic_mongodb_immutable");
    }

    @Bean
    @ApplicationScope
    ProductPriceCalculator productPriceCalculator() {
        return FakeProductPriceCalculator.returning(100);
    }

    @Bean
    @Scope("singleton")
    public static ShoppingCartStore shoppingCartStore(MongoDBEventStore eventStore) {
        return new ShoppingCartStore(eventStore);
    }

    @Primary
    @Bean
    public GlobalExceptionHandler restResponseEntityExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}

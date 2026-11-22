package io.eventdriven.core.projections;

import io.eventdriven.core.configuration.Settings;
import io.eventdriven.core.subscriptions.SubscriptionCheckpoint;
import io.eventdriven.core.subscriptions.SubscriptionCheckpointRepository;
import io.eventdriven.core.subscriptions.SubscriptionCheckpoints;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@AutoConfigurationPackage(basePackageClasses = SubscriptionCheckpoint.class)
public class ProjectionsDatabaseConfig {
    @Bean
    DataSource dataSource(@Value("${projections.database}") String type, DataSourceProperties properties) {
        return switch (type) {
            case "h2" ->
                new EmbeddedDatabaseBuilder()
                        .setType(EmbeddedDatabaseType.H2)
                        .generateUniqueName(true)
                        .build();
            case "postgresql" -> properties.initializeDataSourceBuilder().build();
            default -> throw Settings.unknownValue("projections.database", type, "h2", "postgresql");
        };
    }

    @Bean
    SubscriptionCheckpoints subscriptionCheckpoints(
            SubscriptionCheckpointRepository repository, PlatformTransactionManager transactionManager) {
        return new SubscriptionCheckpoints(repository, transactionManager);
    }
}

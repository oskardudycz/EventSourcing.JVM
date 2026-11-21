package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.core.projections.ProjectionsDatabaseConfig;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContextManager;

public enum ProjectionsDatabase {
    H2(H2Context.class),
    POSTGRESQL(PostgreSQLContext.class);

    private final Class<?> contextClass;

    ProjectionsDatabase(Class<?> contextClass) {
        this.contextClass = contextClass;
    }

    public <Bean> Bean bean(Class<Bean> beanType) {
        return context().getBean(beanType);
    }

    public Statistics statistics() {
        var statistics = context()
                .getBean(EntityManagerFactory.class)
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);

        return statistics;
    }

    private ApplicationContext context() {
        try {
            return new TestContextManager(contextClass).getTestContext().getApplicationContext();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Configuration
    @Import(ProjectionsDatabaseConfig.class)
    @EnableAutoConfiguration
    @EntityScan("io.eventdriven.distributedprocesses")
    @EnableJpaRepositories("io.eventdriven.distributedprocesses")
    static class Projections {}

    @SpringBootTest(classes = Projections.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ActiveProfiles("in-memory")
    static class H2Context {}

    @SpringBootTest(classes = Projections.class, properties = "projections.database=postgresql")
    static class PostgreSQLContext {
        @DynamicPropertySource
        static void postgreSQL(DynamicPropertyRegistry registry) {
            var postgreSQL = TestDatabases.postgreSQL();

            registry.add("spring.datasource.url", postgreSQL::url);
            registry.add("spring.datasource.username", postgreSQL::username);
            registry.add("spring.datasource.password", postgreSQL::password);
        }
    }
}

package io.eventdriven.ecommerce.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = { "io.eventdriven.ecommerce.shoppingcarts", "io.eventdriven.ecommerce.core", "io.eventdriven.ecommerce.api" })
@EnableJpaRepositories(basePackages={"io.eventdriven.ecommerce.shoppingcarts"})
@EntityScan("io.eventdriven.ecommerce.shoppingcarts")
public class ECommerceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ECommerceApplication.class, args);
    }
}

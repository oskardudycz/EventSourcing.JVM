package io.eventdriven.ecommerce.api.config;

import io.eventdriven.ecommerce.shoppingcarts.initializing.InitializeShoppingCart;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Configuration
public class AnnotationConfig {
  @Bean
  public Function<InitializeShoppingCart, CompletableFuture<Void>> handleInitializeShoppingCart() {
    return (command) -> CompletableFuture.allOf();
  }
}

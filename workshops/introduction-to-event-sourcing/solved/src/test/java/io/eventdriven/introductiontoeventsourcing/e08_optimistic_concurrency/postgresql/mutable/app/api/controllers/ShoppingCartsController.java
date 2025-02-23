package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mutable.app.api.controllers;

import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.http.ETag;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mutable.app.api.ShoppingCartsRequests;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mutable.app.shoppingcarts.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mutable.app.shoppingcarts.ShoppingCartStore;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mutable.app.shoppingcarts.productItems.ProductPriceCalculator;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mutable.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mutable.app.shoppingcarts.productItems.ProductItems.ProductItem;

@Validated
@RestController
@RequestMapping("api/shopping-carts")
class ShoppingCartsController {
  private final ShoppingCartStore store;
  private final ProductPriceCalculator productPriceCalculator;

  ShoppingCartsController(
    ShoppingCartStore store,
    ProductPriceCalculator productPriceCalculator
  ) {
    this.store = store;
    this.productPriceCalculator = productPriceCalculator;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<Void> open(
    @Valid @RequestBody ShoppingCartsRequests.Open request
  ) throws URISyntaxException {
    var cartId = UUID.randomUUID();

    var result = store.add(
      cartId,
      ShoppingCart.open(cartId, request.clientId())
    );

    return ResponseEntity
      .created(new URI("api/shopping-carts/%s".formatted(cartId)))
      .eTag(result.value())
      .build();
  }

  @PostMapping("{id}/products")
  ResponseEntity<Void> addProduct(
    @PathVariable UUID id,
    @RequestBody ShoppingCartsRequests.AddProduct request,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) @NotNull ETag ifMatch
  ) {
    if (request.productItem() == null)
      throw new IllegalArgumentException("Product Item has to be defined");

    var result = store.getAndUpdate(
      id,
      ifMatch,
      state -> state.addProduct(
        productPriceCalculator,
        new ProductItem(
          request.productItem().productId(),
          request.productItem().quantity()
        )
      )
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }

  @DeleteMapping("{id}/products/{productId}")
  ResponseEntity<Void> removeProduct(
    @PathVariable UUID id,
    @PathVariable UUID productId,
    @RequestParam @NotNull Integer quantity,
    @RequestParam @NotNull Double price,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) @NotNull ETag ifMatch
  ) {
    var result = store.getAndUpdate(
      id,
      ifMatch,
      state -> state.removeProduct(
        new PricedProductItem(
          productId,
          quantity,
          price
        )
      )
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }

  @PutMapping("{id}")
  ResponseEntity<Void> confirmCart(
    @PathVariable UUID id,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) @NotNull ETag ifMatch
  ) {
    var result = store.getAndUpdate(
      id,
      ifMatch,
      ShoppingCart::confirm
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }

  @DeleteMapping("{id}")
  ResponseEntity<Void> cancelCart(
    @PathVariable UUID id,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) @NotNull ETag ifMatch
  ) {
    var result = store.getAndUpdate(
      id,
      ifMatch,
      ShoppingCart::cancel
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }

  @GetMapping("{id}")
  ResponseEntity<ShoppingCart> getById(
    @PathVariable UUID id
  ) {
    var result = store.get(id);

    return result
      .map(s -> ResponseEntity
        .ok()
        .eTag(s.second().value())
        .body(s.first())
      )
      .orElse(ResponseEntity.notFound().build());
  }
}

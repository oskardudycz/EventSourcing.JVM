package io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.immutable.app.api.controllers;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.immutable.app.api.ShoppingCartsRequests;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.immutable.app.shoppingcarts.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.immutable.app.shoppingcarts.ShoppingCartStore;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.immutable.app.shoppingcarts.productItems.ProductPriceCalculator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.immutable.app.shoppingcarts.ShoppingCartService.ShoppingCartCommand.*;
import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.immutable.app.shoppingcarts.ShoppingCartService.handle;
import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.immutable.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.postgresql.immutable.app.shoppingcarts.productItems.ProductItems.ProductItem;

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

    store.add(
      cartId,
      handle(new OpenShoppingCart(cartId, request.clientId()))
    );

    return ResponseEntity
      .created(new URI("api/shopping-carts/%s".formatted(cartId)))
      .build();
  }

  @PostMapping("{id}/products")
  ResponseEntity<Void> addProduct(
    @PathVariable UUID id,
    @RequestBody ShoppingCartsRequests.AddProduct request
  ) {
    if (request.productItem() == null)
      throw new IllegalArgumentException("Product Item has to be defined");

    store.getAndUpdate(
      id,
      state -> handle(
        productPriceCalculator,
        new AddProductItemToShoppingCart(
          id,
          new ProductItem(
            request.productItem().productId(),
            request.productItem().quantity()
          )
        ),
        state
      )
    );

    return ResponseEntity
      .ok()
      .build();
  }

  @DeleteMapping("{id}/products/{productId}")
  ResponseEntity<Void> removeProduct(
    @PathVariable UUID id,
    @PathVariable UUID productId,
    @RequestParam @NotNull Integer quantity,
    @RequestParam @NotNull Double price
  ) {
    store.getAndUpdate(
      id,
      state -> handle(
        new RemoveProductItemFromShoppingCart(
          id,
          new PricedProductItem(
            productId,
            quantity,
            price
          )
        ),
        state
      )
    );

    return ResponseEntity
      .ok()
      .build();
  }

  @PutMapping("{id}")
  ResponseEntity<Void> confirmCart(
    @PathVariable UUID id
  ) {
    store.getAndUpdate(
      id,
      state -> handle(
        new ConfirmShoppingCart(id),
        state
      )
    );

    return ResponseEntity
      .ok()
      .build();
  }

  @DeleteMapping("{id}")
  ResponseEntity<Void> cancelCart(
    @PathVariable UUID id
  ) {
    store.getAndUpdate(
      id,
      state -> handle(
        new CancelShoppingCart(id),
        state
      )
    );

    return ResponseEntity
      .ok()
      .build();
  }

  @GetMapping("{id}")
  ResponseEntity<ShoppingCart> getById(
    @PathVariable UUID id
  ) {
    var result = store.get(id);

    return result
      .map(s -> ResponseEntity.ok().body(s))
      .orElse(ResponseEntity.notFound().build());
  }
}

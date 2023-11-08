package io.eventdriven.ecommerce.api.controllers;

import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests;
import io.eventdriven.ecommerce.core.entities.CommandHandler;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
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

import static com.eventstore.dbclient.ExpectedRevision.expectedRevision;
import static com.eventstore.dbclient.ExpectedRevision.noStream;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;

@Validated
@RestController
@RequestMapping("api/shopping-carts")
class ShoppingCartsCommandController {
  private final CommandHandler<ShoppingCart, ShoppingCartCommand, ShoppingCartEvent> store;
  private final ProductPriceCalculator productPriceCalculator;

  ShoppingCartsCommandController(
    CommandHandler<ShoppingCart, ShoppingCartCommand, ShoppingCartEvent> store,
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

    var result = store.handle(
      cartId,
      new OpenShoppingCart(
        cartId,
        request.clientId()
      ),
      noStream()
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

    var productItem = productPriceCalculator.calculate(
      new ProductItem(
        request.productItem().productId(),
        request.productItem().quantity()
      )
    );

    var result = store.handle(
      id,
      new AddProductItemToShoppingCart(
        id,
        productItem
      ),
      expectedRevision(ifMatch.toLong())
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

    var productItem =  new PricedProductItem(
      new ProductItem(
        productId,
        quantity
      ),
      price
    );

    var result = store.handle(
      id,
      new RemoveProductItemFromShoppingCart(
        id,
        productItem
      ),
      expectedRevision(ifMatch.toLong())
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
    var result = store.handle(
      id,
      new ConfirmShoppingCart(id),
      expectedRevision(ifMatch.toLong())
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
    var result = store.handle(
      id,
      new CancelShoppingCart(id),
      expectedRevision(ifMatch.toLong())
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }
}

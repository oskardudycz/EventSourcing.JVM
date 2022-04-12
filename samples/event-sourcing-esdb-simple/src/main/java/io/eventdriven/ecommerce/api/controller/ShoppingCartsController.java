package io.eventdriven.ecommerce.api.controller;

import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartService;
import io.eventdriven.ecommerce.shoppingcarts.addingproductitem.AddProductItemToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.canceling.CancelShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.confirming.ConfirmShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.GetShoppingCartById;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetails;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.GetShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfo;
import io.eventdriven.ecommerce.shoppingcarts.opening.OpenShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.ecommerce.shoppingcarts.removingproductitem.RemoveProductItemFromShoppingCart;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("api/shopping-carts")
class ShoppingCartsController {
  private final ShoppingCartService shoppingCartsService;

  ShoppingCartsController(
    ShoppingCartService shoppingCartsService
  ) {
    this.shoppingCartsService = shoppingCartsService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<Void> open(
    @Valid @RequestBody ShoppingCartsRequests.Open request
  ) throws URISyntaxException {
    var cartId = UUID.randomUUID();

    var result = shoppingCartsService.open(
      new OpenShoppingCart(
        cartId,
        request.clientId()
      )
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

    var result = shoppingCartsService.addProductItem(
      new AddProductItemToShoppingCart(
        id,
        new ProductItem(
          request.productItem().productId(),
          request.productItem().quantity()
        ),
        ifMatch.toLong()
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
    var result = shoppingCartsService.removeProductItem(
      new RemoveProductItemFromShoppingCart(
        id,
        new PricedProductItem(
          new ProductItem(
            productId,
            quantity
          ),
          price
        ),
        ifMatch.toLong()
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
    var result = shoppingCartsService.confirm(
      new ConfirmShoppingCart(id, ifMatch.toLong())
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
    var result = shoppingCartsService.cancel(
      new CancelShoppingCart(id, ifMatch.toLong())
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }

  @GetMapping("{id}")
  ResponseEntity<ShoppingCartDetails> getById(
    @PathVariable UUID id,
    @RequestHeader(name = HttpHeaders.IF_NONE_MATCH) @Parameter(in = ParameterIn.HEADER, schema = @Schema(type = "string")) @Nullable ETag ifNoneMatch
  ) {
    var result = shoppingCartsService
      .getById(new GetShoppingCartById(id, ifNoneMatch));

    return ResponseEntity
      .ok()
      .eTag(ETag.weak(result.getVersion()).value())
      .body(result);
  }

  @GetMapping
  List<ShoppingCartShortInfo> get(
    @RequestParam @Nullable Integer pageNumber,
    @RequestParam @Nullable Integer pageSize
  ) {
    return shoppingCartsService
      .getShoppingCarts(GetShoppingCarts.of(pageNumber, pageSize))
      .stream()
      .toList();
  }
}

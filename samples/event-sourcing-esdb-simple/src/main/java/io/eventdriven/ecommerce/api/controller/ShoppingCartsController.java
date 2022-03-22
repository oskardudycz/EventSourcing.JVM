package io.eventdriven.ecommerce.api.controller;

import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests;
import io.eventdriven.ecommerce.core.commands.CommandHandler;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.core.queries.QueryHandler;
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
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/shopping-carts")
public class ShoppingCartsController {
  private final CommandHandler<OpenShoppingCart> handleInitializeShoppingCart;
  private final CommandHandler<AddProductItemToShoppingCart> handleAddProductItemToShoppingCart;
  private final CommandHandler<RemoveProductItemFromShoppingCart> handleRemoveProductItemFromShoppingCart;
  private final CommandHandler<ConfirmShoppingCart> handleConfirmShoppingCart;
  private final CommandHandler<CancelShoppingCart> handleCancelShoppingCart;
  private final QueryHandler<GetShoppingCartById, Optional<ShoppingCartDetails>> handleGetShoppingCartById;
  private final QueryHandler<GetShoppingCarts, Page<ShoppingCartShortInfo>> handleGetShoppingCarts;

  public ShoppingCartsController(
    CommandHandler<OpenShoppingCart> handleInitializeShoppingCart,
    CommandHandler<AddProductItemToShoppingCart> handleAddProductItemToShoppingCart,
    CommandHandler<RemoveProductItemFromShoppingCart> handleRemoveProductItemFromShoppingCart,
    CommandHandler<ConfirmShoppingCart> handleConfirmShoppingCart,
    CommandHandler<CancelShoppingCart> handleCancelShoppingCart,
    QueryHandler<GetShoppingCartById, Optional<ShoppingCartDetails>> handleGetShoppingCartById,
    QueryHandler<GetShoppingCarts, Page<ShoppingCartShortInfo>> handleGetShoppingCarts) {

    this.handleInitializeShoppingCart = handleInitializeShoppingCart;
    this.handleAddProductItemToShoppingCart = handleAddProductItemToShoppingCart;
    this.handleRemoveProductItemFromShoppingCart = handleRemoveProductItemFromShoppingCart;
    this.handleConfirmShoppingCart = handleConfirmShoppingCart;
    this.handleCancelShoppingCart = handleCancelShoppingCart;
    this.handleGetShoppingCartById = handleGetShoppingCartById;
    this.handleGetShoppingCarts = handleGetShoppingCarts;
  }

  @PostMapping
  public ResponseEntity openCart(
    @RequestBody ShoppingCartsRequests.InitializeShoppingCartRequest request
  ) throws ExecutionException, InterruptedException, URISyntaxException {
    var cartId = UUID.randomUUID();

    var command = OpenShoppingCart.of(
      cartId,
      request.clientId()
    );

    return ResponseEntity
      .created(new URI("api/ShoppingCarts/%s".formatted(cartId)))
      .eTag(handleInitializeShoppingCart.handle(command).value())
      .build();
  }

  @PostMapping("{id}/products")
  public ResponseEntity addProduct(
    @PathVariable UUID id,
    @RequestBody ShoppingCartsRequests.AddProductRequest request,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) ETag ifMatch
  ) throws ExecutionException, InterruptedException {
    if (request.productItem() == null)
      throw new IllegalArgumentException("Product Item has to be defined");

    var command = AddProductItemToShoppingCart.of(
      id,
      ProductItem.of(
        request.productItem().productId(),
        request.productItem().quantity()
      ),
      ifMatch.toLong()
    );

    return ResponseEntity
      .ok()
      .eTag(handleAddProductItemToShoppingCart.handle(command).value())
      .build();
  }

  @DeleteMapping("{id}/products/{productId}")
  public ResponseEntity removeProduct(
    @PathVariable UUID id,
    @PathVariable UUID productId,
    @RequestParam Integer quantity,
    @RequestParam Double price,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) ETag ifMatch
  ) throws ExecutionException, InterruptedException {
    var command = RemoveProductItemFromShoppingCart.of(
      id,
      PricedProductItem.of(
        ProductItem.of(
          productId,
          quantity
        ),
        price
      ),
      ifMatch.toLong()
    );

    return ResponseEntity
      .ok()
      .eTag(handleRemoveProductItemFromShoppingCart.handle(command).value())
      .build();
  }

  @PutMapping("{id}")
  public ResponseEntity confirmCart(
    @PathVariable UUID id,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) ETag ifMatch
  ) throws ExecutionException, InterruptedException {
    var command = ConfirmShoppingCart.of(id, ifMatch.toLong());

    return ResponseEntity
      .ok()
      .eTag(handleConfirmShoppingCart.handle(command).value())
      .build();
  }

  @DeleteMapping("{id}")
  public ResponseEntity cancelCart(
    @PathVariable UUID id,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) ETag ifMatch
  ) throws ExecutionException, InterruptedException {
    var command = CancelShoppingCart.of(id, ifMatch.toLong());

    return ResponseEntity
      .ok()
      .eTag(handleCancelShoppingCart.handle(command).value())
      .build();
  }

  @GetMapping("{id}")
  public ResponseEntity<ShoppingCartDetails> get(
    @PathVariable UUID id
  ) {
    return handleGetShoppingCartById.handle(GetShoppingCartById.of(id))
      .map(result ->
        ResponseEntity
          .ok()
          .eTag(ETag.weak(result.getVersion()).value())
          .body(result)
      )
      .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  public List<ShoppingCartShortInfo> get(
    @RequestParam Optional<Integer> pageNumber,
    @RequestParam Optional<Integer> pageSize
  ) {
    return handleGetShoppingCarts.handle(GetShoppingCarts.of(pageNumber, pageSize)).stream().toList();
  }
}

package io.eventdriven.ecommerce.api.controller;

import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests;
import io.eventdriven.ecommerce.core.commands.CommandHandler;
import io.eventdriven.ecommerce.shoppingcarts.addingproductitem.AddProductItemToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.canceling.CancelShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.confirming.ConfirmShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.opening.OpenShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.ecommerce.shoppingcarts.removingproductitem.RemoveProductItemFromShoppingCart;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/shopping-carts")
public class ShoppingCartsController {
  private final CommandHandler<OpenShoppingCart> handleInitializeShoppingCart;
  private final CommandHandler<AddProductItemToShoppingCart> handleAddProductItemToShoppingCart;
  private final CommandHandler<RemoveProductItemFromShoppingCart> handleRemoveProductItemFromShoppingCart;
  private final CommandHandler<ConfirmShoppingCart> handleConfirmShoppingCart;
  private final CommandHandler<CancelShoppingCart> handleCancelShoppingCart;

  public ShoppingCartsController(
    CommandHandler<OpenShoppingCart> handleInitializeShoppingCart,
    CommandHandler<AddProductItemToShoppingCart> handleAddProductItemToShoppingCart,
    CommandHandler<RemoveProductItemFromShoppingCart> handleRemoveProductItemFromShoppingCart,
    CommandHandler<ConfirmShoppingCart> handleConfirmShoppingCart,
    CommandHandler<CancelShoppingCart> handleCancelShoppingCart
  ) {

    this.handleInitializeShoppingCart = handleInitializeShoppingCart;
    this.handleAddProductItemToShoppingCart = handleAddProductItemToShoppingCart;
    this.handleRemoveProductItemFromShoppingCart = handleRemoveProductItemFromShoppingCart;
    this.handleConfirmShoppingCart = handleConfirmShoppingCart;
    this.handleCancelShoppingCart = handleCancelShoppingCart;
  }

  @PostMapping
  public ResponseEntity openCart(
    @RequestBody ShoppingCartsRequests.InitializeShoppingCartRequest request
  ) throws ExecutionException, InterruptedException, URISyntaxException {

    if (request == null)
      throw new IllegalArgumentException("Request body cannot be empty");

    var cartId = UUID.randomUUID();

    var command = OpenShoppingCart.From(
      cartId,
      request.clientId()
    );

    handleInitializeShoppingCart.handle(command).get();

    return ResponseEntity
      .created(new URI("api/ShoppingCarts/%s".formatted(cartId)))
      .build();
  }

  @PostMapping("{id}/products")
  public CompletableFuture<Void> addProduct(
    @PathVariable UUID id,
    @RequestBody ShoppingCartsRequests.AddProductRequest request
  ) throws ExecutionException, InterruptedException {
    if (request == null)
      throw new IllegalArgumentException("Request body cannot be empty");

    if(request.productItem() == null)
      throw new IllegalArgumentException("Product Item has to be defined");

    var command = AddProductItemToShoppingCart.From(
      id,
      ProductItem.From(
        request.productItem().productId(),
        request.productItem().quantity()
      )
    );

    return handleAddProductItemToShoppingCart.handle(command);
  }

  @DeleteMapping("{id}/products/{productId}")
  public CompletableFuture<Void> removeProduct(
    @PathVariable UUID id,
    @PathVariable UUID productId,
    @RequestParam Integer quantity,
    @RequestParam Double price
  ) throws ExecutionException, InterruptedException {
    var command = RemoveProductItemFromShoppingCart.From(
      id,
      PricedProductItem.From(
        ProductItem.From(
          productId,
          quantity
        ),
        price
      )
    );

    return handleRemoveProductItemFromShoppingCart.handle(command);
  }

  @PutMapping("{id}")
  public CompletableFuture<Void> confirmCart(
    @PathVariable UUID id
  ) throws ExecutionException, InterruptedException {
    var command = ConfirmShoppingCart.From(id);

    return handleConfirmShoppingCart.handle(command);
  }


  @DeleteMapping("{id}")
  public CompletableFuture<Void> cancelCart(
    @PathVariable UUID id
  ) throws ExecutionException, InterruptedException {
    var command = ConfirmShoppingCart.From(id);

    return handleConfirmShoppingCart.handle(command);
  }

  @GetMapping
  public List<String> getAll(){
    return List.of("test", "ttt");
  }

//    [HttpGet("{id}")]
//  public async Task<IActionResult> Get(
//        [FromServices] Func<GetCartById, CancellationToken, Task<ShoppingCartDetails?>> query,
//        Guid id,
//        CancellationToken ct
//  )
//  {
//    var result = await query(GetCartById.From(id), ct);
//
//    if (result == null)
//      return NotFound();
//
//    Response.TrySetETagResponseHeader(result.Version.ToString());
//    return Ok(result);
//  }
//
//    [HttpGet]
//  public Task<IReadOnlyList<ShoppingCartShortInfo>> Get(
//        [FromServices] Func<GetCarts, CancellationToken, Task<IReadOnlyList<ShoppingCartShortInfo>>> query,
//        CancellationToken ct,
//        [FromQuery] int pageNumber = 1,
//        [FromQuery] int pageSize = 20
//  ) =>
//  query(GetCarts.From(pageNumber, pageSize), ct);
}

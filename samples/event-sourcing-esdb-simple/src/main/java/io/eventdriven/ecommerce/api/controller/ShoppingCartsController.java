package io.eventdriven.ecommerce.api.controller;

import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests;
import io.eventdriven.ecommerce.shoppingcarts.initializing.InitializeShoppingCart;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

@RestController
@RequestMapping("/shopping-carts")
public class ShoppingCartsController {
  private final Function<InitializeShoppingCart, CompletableFuture<Void>> handleInitializeShoppingCart;

  public ShoppingCartsController(
    Function<InitializeShoppingCart, CompletableFuture<Void>> handleInitializeShoppingCart
  ) {

    this.handleInitializeShoppingCart = handleInitializeShoppingCart;
  }

  @PostMapping
  public ResponseEntity initializeCart(
    @RequestBody ShoppingCartsRequests.InitializeShoppingCartRequest request
  ) throws ExecutionException, InterruptedException, URISyntaxException {

    if (request == null)
      throw new IllegalArgumentException("Request body cannot be empty");

    var cartId = UUID.randomUUID();

    var command = InitializeShoppingCart.From(
      cartId,
      request.clientId()
    );

    handleInitializeShoppingCart.apply(command).get();

    return ResponseEntity
      .created(new URI("api/ShoppingCarts/%s".formatted(cartId)))
      .build();
  }

  @GetMapping
  public List<String> getAll(){
    return List.of("test", "ttt");
  }

//    [HttpPost("{id}/products")]
//  public async Task<IActionResult> AddProduct(
//        [FromServices] Func<AddProductItemToShoppingCart, CancellationToken, ValueTask> handle,
//        [FromRoute] Guid id,
//        [FromBody] AddProductRequest? request,
//        CancellationToken ct
//  )
//  {
//    if (request == null)
//      throw new ArgumentNullException(nameof(request));
//
//    var command = AddProductItemToShoppingCart.From(
//      id,
//      ProductItem.From(
//        request.ProductItem?.ProductId,
//      request.ProductItem?.Quantity
//            )
//        );
//
//    await handle(command, ct);
//
//    return Ok();
//  }
//
//    [HttpDelete("{id}/products/{productId}")]
//  public async Task<IActionResult> RemoveProduct(
//        [FromServices] Func<RemoveProductItemFromShoppingCart, CancellationToken, ValueTask> handle,
//        Guid id,
//        [FromRoute]Guid? productId,
//        [FromQuery]int? quantity,
//        [FromQuery]decimal? unitPrice,
//        CancellationToken ct
//  )
//  {
//    var command = RemoveProductItemFromShoppingCart.From(
//      id,
//      PricedProductItem.From(
//        ProductItem.From(
//          productId,
//          quantity
//        ),
//        unitPrice
//      )
//    );
//
//    await handle(command, ct);
//
//    return NoContent();
//  }
//
//    [HttpPut("{id}/confirmation")]
//  public async Task<IActionResult> ConfirmCart(
//        [FromServices] Func<ConfirmShoppingCart, CancellationToken, ValueTask> handle,
//        Guid id,
//        [FromBody] ConfirmShoppingCartRequest request,
//        CancellationToken ct
//  )
//  {
//    if (request == null)
//      throw new ArgumentNullException(nameof(request));
//
//    var command =
//      ConfirmShoppingCart.From(id);
//
//    await handle(command, ct);
//
//    return Ok();
//  }
//
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

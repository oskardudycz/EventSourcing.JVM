package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;

import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCarts;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers/{customerId}/shopping-carts/current")
@Tag(name = "Shopping carts", description = "The customer's current shopping cart and its checkout")
public class ShoppingCartsController {
    public record AddProductItemRequest(UUID productId, int quantity) {}

    private final ShoppingCartFacade shoppingCarts;
    private final CustomerShoppingCarts customerShoppingCarts;
    private final Carts carts;

    public ShoppingCartsController(
            ShoppingCartFacade shoppingCarts, CustomerShoppingCarts customerShoppingCarts, Carts carts) {
        this.shoppingCarts = shoppingCarts;
        this.customerShoppingCarts = customerShoppingCarts;
        this.carts = carts;
    }

    @GetMapping
    @Operation(operationId = "Cart")
    public ResponseEntity<Cart> get(@PathVariable UUID customerId) {
        var cart = carts.get(currentCartOf(customerId)).orElseThrow();

        return ResponseEntity.ok().eTag(ETag.weak(cart.getVersion()).value()).body(cart);
    }

    @PostMapping("/product-items")
    @Operation(operationId = "AddProductItemToShoppingCart")
    public ResponseEntity<Void> addProductItem(
            @PathVariable UUID customerId, @RequestBody AddProductItemRequest request) {
        var eTag = shoppingCarts.addProductItem(
                new AddProductItemToShoppingCart(customerId, new ProductItem(request.productId(), request.quantity())));

        return ResponseEntity.ok().eTag(eTag.value()).build();
    }

    @DeleteMapping("/product-items")
    @Operation(operationId = "RemoveProductItemFromShoppingCart")
    public ResponseEntity<Void> removeProductItem(
            @PathVariable UUID customerId,
            @RequestParam UUID productId,
            @RequestParam int quantity,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        var eTag = shoppingCarts.removeProductItem(new RemoveProductItemFromShoppingCart(
                currentCartOf(customerId), new ProductItem(productId, quantity), new ETag(ifMatch).toLong()));

        return ResponseEntity.ok().eTag(eTag.value()).build();
    }

    @PostMapping("/checkout")
    @Operation(operationId = "CheckOutShoppingCart")
    public ResponseEntity<?> checkOut(
            @PathVariable UUID customerId, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        var cartId = currentCartOf(customerId);
        var eTag = shoppingCarts.checkOut(new CheckOutShoppingCart(cartId, new ETag(ifMatch).toLong()));

        if (!carts.get(cartId).orElseThrow().isCheckedOut())
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .eTag(eTag.value())
                    .body(ProblemDetail.forStatusAndDetail(
                            HttpStatus.CONFLICT, "Prices changed. Read the cart and check out again."));

        return ResponseEntity.accepted()
                .location(URI.create("/customers/%s/orders/%s".formatted(customerId, cartId.tail())))
                .build();
    }

    private ShoppingCartId currentCartOf(UUID customerId) {
        return customerShoppingCarts
                .currentOf(customerId)
                .orElseThrow(() -> new NoSuchElementException("Customer '%s' has no open cart".formatted(customerId)));
    }
}

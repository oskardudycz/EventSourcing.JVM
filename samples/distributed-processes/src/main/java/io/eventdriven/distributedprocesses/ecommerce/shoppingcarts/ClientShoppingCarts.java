package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public class ClientShoppingCarts extends Projection<ClientShoppingCart> {
  public ClientShoppingCarts(Database database) {
    super(ClientShoppingCart.class, database);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(ProductItemAddedToShoppingCart.class, ShoppingCartCheckedOut.class);
  }

  @Override
  protected EntityId getDocumentId(Object event) {
    return switch (event) {
      case ProductItemAddedToShoppingCart e -> e.shoppingCartId();
      case ShoppingCartCheckedOut e -> e.shoppingCartId();
      default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
    };
  }

  @Override
  protected @Nullable ClientShoppingCart evolve(@Nullable ClientShoppingCart current, Object event) {
    return switch (event) {
      case ProductItemAddedToShoppingCart e -> current != null
        ? current
        : new ClientShoppingCart(e.clientId(), e.shoppingCartId(), ShoppingCartStatus.Pending);
      case ShoppingCartCheckedOut _ -> current != null
        ? new ClientShoppingCart(current.getClientId(), current.getShoppingCartId(), ShoppingCartStatus.CheckedOut)
        : null;
      default -> current;
    };
  }

  public Optional<ShoppingCartId> currentOf(UUID clientId) {
    return find(cart -> cart.getClientId().equals(clientId) && !cart.getStatus().isClosed())
      .stream()
      .map(ClientShoppingCart::getShoppingCartId)
      .findFirst();
  }

  public int countOf(UUID clientId) {
    return find(cart -> cart.getClientId().equals(clientId)).size();
  }
}

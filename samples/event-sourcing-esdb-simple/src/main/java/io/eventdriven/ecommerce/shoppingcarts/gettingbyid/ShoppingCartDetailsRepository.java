package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShoppingCartDetailsRepository
  extends JpaRepository<ShoppingCartDetails, UUID> {
}

package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ShoppingCartDetailsRepository extends JpaRepository<ShoppingCartDetails, UUID> {
    @Query("SELECT d FROM ShoppingCartDetails d WHERE d.id = ?1 AND d.version > ?2")
    Optional<ShoppingCartDetails> findByIdAndNeverVersion(UUID id, long version);
}

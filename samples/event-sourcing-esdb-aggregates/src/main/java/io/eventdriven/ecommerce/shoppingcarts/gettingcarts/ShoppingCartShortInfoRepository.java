package io.eventdriven.ecommerce.shoppingcarts.gettingcarts;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShoppingCartShortInfoRepository
  extends PagingAndSortingRepository<ShoppingCartShortInfo, UUID> {
}

package io.eventdriven.ecommerce.shoppingcarts.gettingcarts;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShoppingCartShortInfoRepository
        extends PagingAndSortingRepository<ShoppingCartShortInfo, UUID>, CrudRepository<ShoppingCartShortInfo, UUID> {}

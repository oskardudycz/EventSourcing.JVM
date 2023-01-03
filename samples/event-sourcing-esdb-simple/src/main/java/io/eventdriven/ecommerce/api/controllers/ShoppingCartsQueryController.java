package io.eventdriven.ecommerce.api.controllers;

import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.GetShoppingCartById;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetails;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.GetShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfo;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfoRepository;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static io.eventdriven.ecommerce.shoppingcarts.gettingbyid.GetShoppingCartById.handle;
import static io.eventdriven.ecommerce.shoppingcarts.gettingcarts.GetShoppingCarts.handle;

@Validated
@RestController
@RequestMapping("api/shopping-carts")
class ShoppingCartsQueryController {
  private final ShoppingCartShortInfoRepository shortInfoRepository;
  private final ShoppingCartDetailsRepository detailsRepository;

  ShoppingCartsQueryController(
    ShoppingCartShortInfoRepository shortInfoRepository,
    ShoppingCartDetailsRepository detailsRepository
  ) {
    this.shortInfoRepository = shortInfoRepository;
    this.detailsRepository = detailsRepository;
  }

  @GetMapping("{id}")
  ResponseEntity<ShoppingCartDetails> getById(
    @PathVariable UUID id,
    @RequestHeader(name = HttpHeaders.IF_NONE_MATCH) @Parameter(in = ParameterIn.HEADER, schema = @Schema(type = "string")) @Nullable ETag ifNoneMatch
  ) {
    var result = handle(detailsRepository, new GetShoppingCartById(id, ifNoneMatch));

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
    return handle(shortInfoRepository, GetShoppingCarts.of(pageNumber, pageSize))
      .stream()
      .toList();
  }
}

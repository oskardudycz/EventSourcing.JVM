package io.eventdriven.ecommerce.shop;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.eventdriven.testing.api.ApiE2ESpecification.TestRequest;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.MediaType;

public final class ShopRequests {
    private ShopRequests() {}

    public static UUID customerIdOf(String name) {
        return UUID.nameUUIDFromBytes(name.strip().toLowerCase(Locale.ROOT).getBytes(UTF_8));
    }

    public static String productsOf(String name) {
        return "/shop/customers/%s/products".formatted(customerIdOf(name));
    }

    public static TestRequest openShop() {
        return request -> request.get().uri("/shop");
    }

    public static TestRequest openShopAs(String name) {
        return request -> request.get().uri("/shop").cookie("customer", URLEncoder.encode(name, UTF_8));
    }

    public static TestRequest loginPage() {
        return request -> request.get().uri("/shop/login");
    }

    public static TestRequest logIn(String name) {
        return request -> request.post()
                .uri("/shop/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("name=" + URLEncoder.encode(name, UTF_8));
    }

    public static TestRequest productsPageAs(String name) {
        return request -> request.get().uri(productsOf(name)).cookie("customer", URLEncoder.encode(name, UTF_8));
    }

    public static TestRequest logOut(String name) {
        return request -> request.post().uri("/shop/logout").cookie("customer", URLEncoder.encode(name, UTF_8));
    }
}

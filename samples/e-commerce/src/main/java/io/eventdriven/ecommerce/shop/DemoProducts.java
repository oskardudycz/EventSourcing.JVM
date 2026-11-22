package io.eventdriven.ecommerce.shop;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DemoProducts {
    public record Product(UUID id, String name, String description) {}

    private static final List<Product> products = List.of(
            new Product(
                    UUID.fromString("6f1d3a52-0c5e-4b8a-9d61-1f3c2a7e9b01"),
                    "Trail running shoes",
                    "Light shoes with a grippy sole for wet trails."),
            new Product(
                    UUID.fromString("6f1d3a52-0c5e-4b8a-9d61-1f3c2a7e9b02"),
                    "Merino T-shirt",
                    "Soft wool that stays fresh on long days."),
            new Product(
                    UUID.fromString("6f1d3a52-0c5e-4b8a-9d61-1f3c2a7e9b03"),
                    "Rain jacket",
                    "Waterproof shell that packs into its own pocket."),
            new Product(
                    UUID.fromString("6f1d3a52-0c5e-4b8a-9d61-1f3c2a7e9b04"),
                    "Hiking socks",
                    "Cushioned socks, two pairs."));

    private DemoProducts() {}

    public static List<Product> all() {
        return products;
    }

    public static Map<UUID, String> names() {
        return products.stream().collect(Collectors.toMap(Product::id, Product::name));
    }
}

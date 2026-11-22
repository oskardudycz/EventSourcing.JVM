package io.eventdriven.ecommerce.shipments.sandbox;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shop.DemoProducts;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/sandbox/warehouse")
public class SandboxWarehousePage {
    public record ProductStock(UUID productId, String name, boolean inStock) {}

    public record PackageRow(String shipmentTail, String order, String products, SandboxWarehouse.Status status) {}

    private final SandboxWarehouse warehouse;

    public SandboxWarehousePage(SandboxWarehouse warehouse) {
        this.warehouse = warehouse;
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute(
                "products",
                DemoProducts.all().stream()
                        .map(product ->
                                new ProductStock(product.id(), product.name(), warehouse.isInStock(product.id())))
                        .toList());
        model.addAttribute(
                "packages",
                warehouse.packages().stream().map(SandboxWarehousePage::rowOf).toList());
        return "sandbox/warehouse";
    }

    @PostMapping("/products/{productId}/stock")
    public RedirectView stock(@PathVariable UUID productId, @RequestParam boolean inStock) {
        warehouse.stock(productId, inStock);
        return backToTheWarehouse();
    }

    @PostMapping("/shipments/{shipmentTail}/send")
    public RedirectView send(@PathVariable String shipmentTail) {
        return act(shipmentTail, warehouse::send);
    }

    @PostMapping("/shipments/{shipmentTail}/reject")
    public RedirectView reject(@PathVariable String shipmentTail) {
        return act(shipmentTail, warehouse::reject);
    }

    @PostMapping("/shipments/{shipmentTail}/deliver")
    public RedirectView deliver(@PathVariable String shipmentTail) {
        return act(shipmentTail, warehouse::deliver);
    }

    private RedirectView act(String shipmentTail, Consumer<Shipment.Id> action) {
        var shipmentId = new Shipment.Id(Urns.of("ecommerce", "shipment", shipmentTail));
        if (warehouse.find(shipmentId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The warehouse holds no such shipment");

        action.accept(shipmentId);
        return backToTheWarehouse();
    }

    private static RedirectView backToTheWarehouse() {
        var redirect = new RedirectView("/sandbox/warehouse", true);
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        return redirect;
    }

    private static PackageRow rowOf(SandboxWarehouse.Package pack) {
        var tail = pack.shipmentId().tail();
        var names = DemoProducts.names();
        var products = Arrays.stream(pack.productItems())
                .map(item -> "%d × %s"
                        .formatted(
                                item.quantity(), names.getOrDefault(item.productId(), "Product " + item.productId())))
                .collect(Collectors.joining(", "));

        return new PackageRow(
                tail,
                "Order #%s of customer %s".formatted(tail.substring(tail.lastIndexOf('-') + 1), tail.substring(0, 8)),
                products,
                pack.status());
    }
}

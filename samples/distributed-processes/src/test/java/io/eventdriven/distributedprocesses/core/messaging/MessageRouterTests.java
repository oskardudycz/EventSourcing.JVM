package io.eventdriven.distributedprocesses.core.messaging;

import static io.eventdriven.distributedprocesses.core.messaging.MessageRoutes.inPackage;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class MessageRouterTests {
    public record RoomInspected(UUID roomId, String notes) {}

    public sealed interface GuestEvent {
        String guestId();
    }

    public record GuestMoved(String hotelId, String guestId) implements GuestEvent {}

    @Test
    public void messageWithoutRegisteredRoute_HasNoTargetAndNoKey() {
        var router = new MessageRouter(List.of());

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget(null, null));
    }

    @Test
    public void registeredKey_LeavesTheTargetEmpty() {
        var router = new MessageRouter(List.of(new MessageRoutes().routeKey(GuestMoved.class, GuestMoved::guestId)));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget(null, "guest-1"));
    }

    @Test
    public void registeredRoute_GivesTargetAndKey() {
        var router = new MessageRouter(List.of(new MessageRoutes()
                .route(
                        RoomInspected.class,
                        message -> new RoutingTarget("rooms", message.roomId().toString()))));
        var roomId = UUID.randomUUID();

        assertThat(router.routeFor(new RoomInspected(roomId, "clean")))
                .isEqualTo(new RoutingTarget("rooms", roomId.toString()));
    }

    @Test
    public void routeRegisteredForAnInterface_AppliesToItsImplementations() {
        var router = new MessageRouter(List.of(new MessageRoutes().routeKey(GuestEvent.class, GuestEvent::guestId)));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget(null, "guest-1"));
    }

    @Test
    public void routesFromSeveralRegistrations_AreAllUsed() {
        var router = new MessageRouter(List.of(
                new MessageRoutes().routeKey(GuestMoved.class, GuestMoved::guestId),
                new MessageRoutes()
                        .route(RoomInspected.class, message -> new RoutingTarget("rooms", message.notes()))));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget(null, "guest-1"));
        assertThat(router.routeFor(new RoomInspected(UUID.randomUUID(), "clean")))
                .isEqualTo(new RoutingTarget("rooms", "clean"));
    }

    @Test
    public void registeredTarget_LeavesTheKeyEmpty() {
        var router = new MessageRouter(List.of(new MessageRoutes().routeTarget(GuestMoved.class, message -> "guests")));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget("guests", null));
    }

    @Test
    public void targetRegisteredForAPackage_AppliesToMessagesFromItAndItsSubpackages() {
        var router = new MessageRouter(List.of(new MessageRoutes()
                .routeTarget(inPackage("io.eventdriven.distributedprocesses.core"), "hotel-messages")));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
                .isEqualTo(new RoutingTarget("hotel-messages", null));
    }

    @Test
    public void targetRegisteredForAPackage_DoesNotApplyToOtherPackages() {
        var router = new MessageRouter(List.of(new MessageRoutes()
                .routeTarget(inPackage("io.eventdriven.distributedprocesses.core.mess"), "hotel-messages")));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget(null, null));
    }

    @Test
    public void routeRegisteredForAPackage_AppliesToMessagesFromIt() {
        var router = new MessageRouter(List.of(new MessageRoutes()
                .route(
                        inPackage(MessageRouterTests.class.getPackageName()),
                        message -> new RoutingTarget("hotel-messages", "any-key"))));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
                .isEqualTo(new RoutingTarget("hotel-messages", "any-key"));
    }

    @Test
    public void targetAndKeyFromDifferentRegistrations_AreCombined() {
        var router = new MessageRouter(List.of(new MessageRoutes()
                .routeTarget(inPackage(MessageRouterTests.class.getPackageName()), "hotel-messages")
                .routeKey(GuestMoved.class, GuestMoved::guestId)));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
                .isEqualTo(new RoutingTarget("hotel-messages", "guest-1"));
    }

    @Test
    public void earlierRegistration_Wins() {
        var router = new MessageRouter(List.of(new MessageRoutes()
                .routeKey(GuestMoved.class, GuestMoved::guestId)
                .routeKey(GuestEvent.class, message -> "any-guest")));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget(null, "guest-1"));
    }

    @Test
    public void registeredKeyResolvedToNull_LeavesTheKeyEmpty() {
        var router = new MessageRouter(List.of(new MessageRoutes().routeKey(GuestMoved.class, message -> null)));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget(null, null));
    }

    @Test
    public void registeredRouteWithNullKey_LeavesTheKeyEmpty() {
        var router = new MessageRouter(
                List.of(new MessageRoutes().route(GuestMoved.class, message -> new RoutingTarget("guests", null))));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget("guests", null));
    }

    @Test
    public void registeredRouteResolvedToNull_HasNoTargetAndNoKey() {
        var router = new MessageRouter(List.of(new MessageRoutes().route(GuestMoved.class, message -> null)));

        assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1"))).isEqualTo(new RoutingTarget(null, null));
    }
}

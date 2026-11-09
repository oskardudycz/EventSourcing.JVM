package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRouter.firstRecordComponent;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes.inPackage;
import static org.assertj.core.api.Assertions.assertThat;

public class MessageRouterTest {
  public record RoomInspected(UUID roomId, String notes) {
  }

  public sealed interface GuestEvent {
    String guestId();
  }

  public record GuestMoved(String hotelId, String guestId) implements GuestEvent {
  }

  public record HousekeepingStarted() {
  }

  public static class RoomCleaned {
  }

  private static RoutingTarget byConvention(Object message) {
    return new RoutingTarget("hotel", message.getClass().getSimpleName());
  }

  @Test
  public void messageWithoutRegisteredRoute_FollowsTheConvention() {
    var router = new MessageRouter(List.of(), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel", "GuestMoved"));
  }

  @Test
  public void registeredKey_KeepsTheConventionalTarget() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().routeKey(GuestMoved.class, GuestMoved::guestId)
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel", "guest-1"));
  }

  @Test
  public void registeredRoute_OverridesTargetAndKey() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().route(RoomInspected.class, message -> new RoutingTarget("rooms", message.roomId().toString()))
    ), MessageRouterTest::byConvention);
    var roomId = UUID.randomUUID();

    assertThat(router.routeFor(new RoomInspected(roomId, "clean")))
      .isEqualTo(new RoutingTarget("rooms", roomId.toString()));
  }

  @Test
  public void routeRegisteredForAnInterface_AppliesToItsImplementations() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().routeKey(GuestEvent.class, GuestEvent::guestId)
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel", "guest-1"));
  }

  @Test
  public void routesFromSeveralRegistrations_AreAllUsed() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().routeKey(GuestMoved.class, GuestMoved::guestId),
      new MessageRoutes().route(RoomInspected.class, message -> new RoutingTarget("rooms", message.notes()))
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel", "guest-1"));
    assertThat(router.routeFor(new RoomInspected(UUID.randomUUID(), "clean")))
      .isEqualTo(new RoutingTarget("rooms", "clean"));
  }

  @Test
  public void registeredTarget_KeepsTheConventionalKey() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().routeTarget(GuestMoved.class, message -> "guests")
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("guests", "GuestMoved"));
  }

  @Test
  public void targetRegisteredForAPackage_AppliesToMessagesFromItAndItsSubpackages() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().routeTarget(inPackage("io.eventdriven.eventdrivenarchitecture.e03_businessprocesses"), "hotel-messages")
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel-messages", "GuestMoved"));
  }

  @Test
  public void targetRegisteredForAPackage_DoesNotApplyToOtherPackages() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().routeTarget(inPackage("io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.co"), "hotel-messages")
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel", "GuestMoved"));
  }

  @Test
  public void routeRegisteredForAPackage_AppliesToMessagesFromIt() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().route(
        inPackage(MessageRouterTest.class.getPackageName()),
        message -> new RoutingTarget("hotel-messages", firstRecordComponent(message))
      )
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel-messages", "hotel-1"));
  }

  @Test
  public void targetAndKeyFromDifferentRegistrations_AreCombined() {
    var router = new MessageRouter(List.of(
      new MessageRoutes()
        .routeTarget(inPackage(MessageRouterTest.class.getPackageName()), "hotel-messages")
        .routeKey(GuestMoved.class, GuestMoved::guestId)
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel-messages", "guest-1"));
  }

  @Test
  public void earlierRegistration_Wins() {
    var router = new MessageRouter(List.of(
      new MessageRoutes()
        .routeKey(GuestMoved.class, GuestMoved::guestId)
        .routeKey(GuestEvent.class, message -> "any-guest")
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel", "guest-1"));
  }

  @Test
  public void registeredKeyResolvedToNull_FallsBackToTheConventionalKey() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().routeKey(GuestMoved.class, message -> null)
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel", "GuestMoved"));
  }

  @Test
  public void registeredTargetResolvedToNull_FallsBackToTheConventionalTarget() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().routeTarget(GuestMoved.class, message -> null)
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel", "GuestMoved"));
  }

  @Test
  public void registeredRouteWithNullKey_FallsBackToTheConventionalKey() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().route(GuestMoved.class, message -> new RoutingTarget("guests", null))
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("guests", "GuestMoved"));
  }

  @Test
  public void registeredRouteResolvedToNull_FollowsTheConvention() {
    var router = new MessageRouter(List.of(
      new MessageRoutes().route(GuestMoved.class, message -> null)
    ), MessageRouterTest::byConvention);

    assertThat(router.routeFor(new GuestMoved("hotel-1", "guest-1")))
      .isEqualTo(new RoutingTarget("hotel", "GuestMoved"));
  }

  @Test
  public void record_FirstComponentIsItsValue() {
    var roomId = UUID.randomUUID();

    assertThat(firstRecordComponent(new RoomInspected(roomId, "clean"))).isEqualTo(roomId.toString());
  }

  @Test
  public void recordWithNullFirstComponent_HasNoFirstComponent() {
    assertThat(firstRecordComponent(new RoomInspected(null, "clean"))).isNull();
  }

  @Test
  public void recordWithoutComponents_HasNoFirstComponent() {
    assertThat(firstRecordComponent(new HousekeepingStarted())).isNull();
  }

  @Test
  public void messageThatIsNotARecord_HasNoFirstComponent() {
    assertThat(firstRecordComponent(new RoomCleaned())).isNull();
  }
}

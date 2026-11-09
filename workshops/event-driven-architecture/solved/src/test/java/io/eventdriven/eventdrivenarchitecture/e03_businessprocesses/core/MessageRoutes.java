package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class MessageRoutes {
  private final List<Registration> registrations = new ArrayList<>();

  public <Message> MessageRoutes route(Class<Message> messageClass, Function<Message, @Nullable RoutingTarget> route) {
    return register(messageClass, message -> toRoute(route.apply(message)));
  }

  public MessageRoutes route(Predicate<Object> selector, Function<Object, @Nullable RoutingTarget> route) {
    registrations.add(new Registration(selector, message -> toRoute(route.apply(message))));
    return this;
  }

  public <Message> MessageRoutes routeTarget(Class<Message> messageClass, Function<Message, @Nullable String> target) {
    return register(messageClass, message -> new Route(target.apply(message), null));
  }

  public MessageRoutes routeTarget(Predicate<Object> selector, String target) {
    registrations.add(new Registration(selector, message -> new Route(target, null)));
    return this;
  }

  public <Message> MessageRoutes routeKey(Class<Message> messageClass, Function<Message, ?> key) {
    return register(messageClass, message -> {
      var value = key.apply(message);
      return new Route(null, value != null ? value.toString() : null);
    });
  }

  public static Predicate<Object> inPackage(String packageName) {
    return message -> {
      var messagePackage = message.getClass().getPackageName();
      return messagePackage.equals(packageName) || messagePackage.startsWith(packageName + ".");
    };
  }

  List<Registration> getRegistrations() {
    return registrations;
  }

  private <Message> MessageRoutes register(Class<Message> messageClass, Function<Message, Route> route) {
    registrations.add(new Registration(messageClass::isInstance, message -> route.apply(messageClass.cast(message))));
    return this;
  }

  private static Route toRoute(@Nullable RoutingTarget routed) {
    return routed != null ? new Route(routed.target(), routed.key()) : Route.NONE;
  }

  record Registration(Predicate<Object> matches, Function<Object, Route> route) {
  }

  record Route(@Nullable String target, @Nullable String key) {
    static final Route NONE = new Route(null, null);
  }
}

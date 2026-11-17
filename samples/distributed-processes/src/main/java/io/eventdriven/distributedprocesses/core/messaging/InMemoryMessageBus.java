package io.eventdriven.distributedprocesses.core.messaging;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InMemoryMessageBus implements MessageBus {
  private record Binding(String queue, String[] pattern) {
  }

  private final List<MessageRoutes> routes = new ArrayList<>();
  private MessageRouter router = routerFor(routes);
  private final Map<String, List<Binding>> bindings = new LinkedHashMap<>();
  private final Map<String, Map<String, List<Consumer<Object>>>> queues = new LinkedHashMap<>();
  private final List<Consumer<Object>> middlewares = new ArrayList<>();

  public <Message> void publish(List<Message> messages) {
    for (Object message : messages) {

      for (var middleware : middlewares)
        middleware.accept(message);

      var route = router.routeFor(message);

      if (route.target() == null)
        continue;

      for (var binding : bindings.getOrDefault(route.target(), List.of())) {
        if (!matches(binding.pattern(), route.key()))
          continue;

        var handlers = queues.getOrDefault(binding.queue(), Map.of()).get(message.getClass().getTypeName());

        if (handlers != null)
          for (var handle : handlers) {
            handle.accept(message);
          }
      }
    }
  }

  public InMemoryMessageBus routes(MessageRoutes moduleRoutes) {
    routes.add(moduleRoutes);
    router = routerFor(routes);
    return this;
  }

  public InMemoryMessageBus bind(String queue, String channel, String routingPattern) {
    bindings.computeIfAbsent(channel, ignored -> new ArrayList<>())
      .add(new Binding(queue, routingPattern.split("\\.")));
    return this;
  }

  public <Message> InMemoryMessageBus subscribe(String queue, Class<Message> messageClass, Consumer<Message> handler) {
    queues.computeIfAbsent(queue, ignored -> new LinkedHashMap<>())
      .computeIfAbsent(messageClass.getTypeName(), ignored -> new ArrayList<>())
      .add(message -> handler.accept(messageClass.cast(message)));
    return this;
  }

  public void use(Consumer<Object> middleware) {
    middlewares.add(middleware);
  }

  private static MessageRouter routerFor(List<MessageRoutes> routes) {
    return new MessageRouter(List.copyOf(routes), message -> new RoutingTarget(null, null));
  }

  private static boolean matches(String[] pattern, @Nullable String key) {
    var words = key != null ? key.split("\\.") : new String[0];
    return matches(pattern, 0, words, 0);
  }

  private static boolean matches(String[] pattern, int p, String[] words, int w) {
    if (p == pattern.length)
      return w == words.length;

    if (pattern[p].equals("#"))
      return matches(pattern, p + 1, words, w) || (w < words.length && matches(pattern, p, words, w + 1));

    return w < words.length
      && (pattern[p].equals("*") || pattern[p].equals(words[w]))
      && matches(pattern, p + 1, words, w + 1);
  }
}

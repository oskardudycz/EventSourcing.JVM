package io.eventdriven.introductiontoeventsourcing.e06_business_logic.occurrent.immutable.solution1;

import one.util.streamex.StreamEx;
import org.occurrent.application.service.blocking.ApplicationService;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class CommandHandler {

  public static <Entity, Event, Command> BiConsumer<UUID, Command> commandHandler(
    ApplicationService<Event> applicationService,
    Supplier<Entity> getEmpty,
    Function<UUID, String> toStreamName,
    BiFunction<Entity, Event, Entity> when,
    BiFunction<Command, Entity, Event> handle
  ) {
    return (id, command) -> {
      var streamName = toStreamName.apply(id);

      applicationService.execute(streamName, e -> {
        var entity = StreamEx.of(e).foldLeft(getEmpty.get(), when);
        return Stream.of(handle.apply(command, entity));
      });
    };
  }
}

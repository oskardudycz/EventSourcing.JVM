package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingFailureStrategy implements MessageFailureStrategy {
  public record Failure(String messageType, Optional<Object> message) {
  }

  private final MessageFailureStrategy strategy = MessageFailureStrategy.retryUnlessUnreadable();
  public final List<Failure> failures = new CopyOnWriteArrayList<>();

  @Override
  public Decision onFailure(String messageType, Optional<Object> message, Throwable error) {
    failures.add(new Failure(messageType, message));
    return strategy.onFailure(messageType, message, error);
  }

  public static class Config {
    @Bean
    @Primary
    public RecordingFailureStrategy recordingFailureStrategy() {
      return new RecordingFailureStrategy();
    }
  }
}

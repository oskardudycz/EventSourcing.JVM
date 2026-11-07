package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class RetryBackOffTest {
  @Test
  public void retriesTwice_WithDoublingJitteredDelays_ThenStops() {
    var backOff = RetryBackOff.exponential(Duration.ofSeconds(1));

    for (var i = 0; i < 100; i++) {
      var execution = backOff.start();

      assertThat(execution.nextBackOff()).as("first retry").isBetween(1000L, 1500L);
      assertThat(execution.nextBackOff()).as("second retry, doubled, jitter doubled too").isBetween(1000L, 3000L);
      assertThat(execution.nextBackOff()).as("no third retry").isEqualTo(BackOffExecution.STOP);
    }
  }
}

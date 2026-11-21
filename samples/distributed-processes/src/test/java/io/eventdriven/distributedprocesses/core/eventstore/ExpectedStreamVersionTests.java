package io.eventdriven.distributedprocesses.core.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class ExpectedStreamVersionTests {
    @Test
    public void expectingTheFirstEventsVersionIsAllowed() {
        assertThat(ExpectedStreamVersion.at(0)).isEqualTo(new ExpectedStreamVersion.At(0));
    }

    @Test
    public void expectingANegativeVersionIsRefusedNamingTheVersion() {
        assertThatThrownBy(() -> ExpectedStreamVersion.at(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }
}

package io.eventdriven.distributedprocesses.core.configuration;

public final class Settings {
    private Settings() {}

    public static IllegalArgumentException unknownValue(String setting, String value, String... allowedValues) {
        return new IllegalArgumentException(
                "%s must be one of %s, but was '%s'".formatted(setting, String.join(", ", allowedValues), value));
    }
}

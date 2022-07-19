package io.eventdriven.decider.core.processing;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.regex.Pattern;

public record ETag(String value) {
  private static final Pattern ETagPattern = Pattern.compile("\"([^\"]*)\"");

  @JsonCreator
  public ETag {
    if (value != null) {
      var regexMatcher = ETagPattern.matcher(value);

      if (!regexMatcher.find())
        throw new IllegalArgumentException("Not an ETag header");
    }
  }

  public static ETag weak(Object value) {
    return new ETag("W/\"%s\"".formatted(value.toString()));
  }

  public boolean isEmpty() {
    return value == null;
  }

  public long toLong() {
    if (value == null) {
      throw new IllegalStateException("Header is empty");
    }

    var regexMatcher = ETagPattern.matcher(value);
    regexMatcher.find();

    return Long.parseLong(regexMatcher.group(1));
  }
}

package io.eventdriven.ecommerce.core.http;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.regex.Pattern;

public record ETag(String value) {
  private static final Pattern ETagPattern = Pattern.compile("\"([^\"]*)\"");

  @JsonCreator
  public ETag{
    var regexMatcher = ETagPattern.matcher(value);

    if(!regexMatcher.find())
      throw new IllegalArgumentException("Not an ETag header");
  }

  public static ETag weak(Object value){
    return new ETag("W/\"%s\"".formatted(value.toString()));
  }

  public Long toLong() {
    var regexMatcher = ETagPattern.matcher(value);
    regexMatcher.find();

    return Long.parseLong(regexMatcher.group(1));
  }
}

package io.eventdriven.uniqueness.core.resourcereservation;

public final class Hash {
  /**
   * Simple hash function for string with additional randomness logic.
   * <a href="https://stackoverflow.com/a/1660613/10966454">Source</a>
   * @param string input string
   * @return hashed long value
   */
  public static Long hash(String string) {
    long h = 1125899906842597L; // prime
    int len = string.length();

    for (int i = 0; i < len; i++) {
      h = 31*h + string.charAt(i);
    }
    return h;
  }
}

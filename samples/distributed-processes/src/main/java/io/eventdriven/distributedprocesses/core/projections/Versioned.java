package io.eventdriven.distributedprocesses.core.projections;

public interface Versioned {
  long getVersion();

  void setVersion(long version);
}

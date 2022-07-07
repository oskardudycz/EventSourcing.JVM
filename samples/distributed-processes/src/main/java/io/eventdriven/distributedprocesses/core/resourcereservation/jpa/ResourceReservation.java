package io.eventdriven.distributedprocesses.core.resourcereservation.jpa;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.persistence.*;
import java.time.OffsetDateTime;

@Entity
public class ResourceReservation {
  public enum Status {
    Pending,
    Confirmed
  }

  @Id
  private String resourceKey;

  @Column()
  private OffsetDateTime lockedTill;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private Status status;

  @Column(nullable = false)
  private OffsetDateTime initiatedAt;

  @Column(nullable = true)
  private OffsetDateTime reservedAt;

  public ResourceReservation(
    String resourceKey,
    OffsetDateTime lockedTill,
    Status status,
    OffsetDateTime initiatedAt,
    OffsetDateTime reservedAt
  ) {
    this.setResourceKey(resourceKey);
    this.setLockedTill(lockedTill);
    this.setStatus(status);
    this.setInitiatedAt(initiatedAt);
    this.setReservedAt(reservedAt);
  }

  public ResourceReservation() {

  }

  public String getResourceKey() {
    return resourceKey;
  }

  public void setResourceKey(String resourceKey) {
    this.resourceKey = resourceKey;
  }

  public OffsetDateTime getLockedTill() {
    return lockedTill;
  }

  public void setLockedTill(OffsetDateTime lockedTill) {
    this.lockedTill = lockedTill;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public void confirm(OffsetDateTime reservedAt) {
    setStatus(Status.Confirmed);
    setReservedAt(OffsetDateTime.now());
  }

  public boolean isConfirmed() {
    return getStatus() == Status.Confirmed;
  }

  public OffsetDateTime getInitiatedAt() {
    return initiatedAt;
  }

  public void setInitiatedAt(OffsetDateTime initiatedAt) {
    this.initiatedAt = initiatedAt;
  }

  public OffsetDateTime getReservedAt() {
    return reservedAt;
  }

  public void setReservedAt(OffsetDateTime reservedAt) {
    this.reservedAt = reservedAt;
  }
}



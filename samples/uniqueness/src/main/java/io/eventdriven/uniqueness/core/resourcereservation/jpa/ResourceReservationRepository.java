package io.eventdriven.uniqueness.core.resourcereservation.jpa;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ResourceReservationRepository extends CrudRepository<ResourceReservation, String> {
  @Query("select r from ResourceReservation r where r.lockedTill < ?1")
  List<ResourceReservation> getTimedOut(OffsetDateTime dateTime);
}

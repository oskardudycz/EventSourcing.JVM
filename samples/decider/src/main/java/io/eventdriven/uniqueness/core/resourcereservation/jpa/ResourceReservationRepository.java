package io.eventdriven.uniqueness.core.resourcereservation.jpa;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ResourceReservationRepository extends CrudRepository<ResourceReservation, String> {
  @Query(value = "select * from resource_reservation r where r.locked_till < :dateTime AND r.status = 'Pending'", nativeQuery = true)
  List<ResourceReservation> getTimedOut(@Param(value = "dateTime")  OffsetDateTime dateTime);
}

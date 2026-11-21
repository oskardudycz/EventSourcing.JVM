package io.eventdriven.distributedprocesses.core.projections;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessorCheckpointRepository extends JpaRepository<ProcessorCheckpoint, String> {}

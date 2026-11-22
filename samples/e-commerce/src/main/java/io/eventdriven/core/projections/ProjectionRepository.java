package io.eventdriven.core.projections;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

@NoRepositoryBean
public interface ProjectionRepository<Document extends Versioned, Id> extends JpaRepository<Document, Id> {
    @Transactional
    default void inTransaction(Runnable work) {
        work.run();
    }
}

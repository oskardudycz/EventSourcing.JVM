package io.eventdriven.core.projections;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.projections.CounterEvent.*;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class ProjectionTests {
    private final CounterId counterId = new CounterId(UUID.randomUUID().toString());
    private final CounterId otherCounterId = new CounterId(UUID.randomUUID().toString());
    private final ProjectionsDatabase database;
    private final CounterProjection projection;

    ProjectionTests(ProjectionsDatabase database) {
        this.database = database;
        projection = new CounterProjection(database.bean(CounterRepository.class));
    }

    @Test
    public void firstEventStoresTheDocumentAtTheEventsStreamPosition() {
        projection.handle(at(0, new Started(counterId)));

        assertThat(projection.get(counterId)).hasValueSatisfying(counter -> {
            assertThat(counter.getCount()).isEqualTo(0);
            assertThat(counter.getVersion()).isEqualTo(0);
        });
    }

    @Test
    public void nextEventEvolvesTheStoredDocument() {
        projection.handle(at(0, new Started(counterId)));

        projection.handle(at(1, new Incremented(counterId)));

        assertThat(projection.get(counterId)).hasValueSatisfying(counter -> {
            assertThat(counter.getCount()).isEqualTo(1);
            assertThat(counter.getVersion()).isEqualTo(1);
        });
    }

    @Test
    public void anEventTheDocumentAlreadyAppliedChangesNothing() {
        projection.handle(at(0, new Started(counterId)));
        var incremented = at(1, new Incremented(counterId));

        projection.handle(incremented);
        projection.handle(incremented);

        assertThat(projection.get(counterId))
                .hasValueSatisfying(counter -> assertThat(counter.getCount()).isEqualTo(1));
    }

    @Test
    public void eventOlderThanTheDocumentIsSkipped() {
        projection.handle(at(0, new Started(counterId)));
        projection.handle(at(2, new Incremented(counterId)));

        projection.handle(at(1, new Incremented(counterId)));

        assertThat(projection.get(counterId)).hasValueSatisfying(counter -> {
            assertThat(counter.getCount()).isEqualTo(1);
            assertThat(counter.getVersion()).isEqualTo(2);
        });
    }

    @Test
    public void eventThatChangesNoFieldStillMovesTheVersion() {
        projection.handle(at(0, new Started(counterId)));

        projection.handle(at(1, new Paused(counterId)));

        assertThat(projection.get(counterId)).hasValueSatisfying(counter -> {
            assertThat(counter.getCount()).isEqualTo(0);
            assertThat(counter.getVersion()).isEqualTo(1);
        });
    }

    @Test
    public void evolveReturningNoDocumentDeletesIt() {
        projection.handle(at(0, new Started(counterId)));

        projection.handle(at(1, new Finished(counterId)));

        assertThat(projection.get(counterId)).isEmpty();
    }

    @Test
    public void aViewQueryReturnsOnlyTheMatchingDocuments() {
        projection.handle(at(0, new Started(counterId)));
        projection.handle(at(1, new Incremented(counterId)));
        projection.handle(at(0, new Started(otherCounterId)));

        assertThat(projection.countedAtLeastOnce()).contains(counterId).doesNotContain(otherCounterId);
    }

    @Test
    public void evolveReturningNoDocumentWhenNothingIsStoredChangesNothing() {
        projection.handle(at(0, new Finished(counterId)));

        assertThat(projection.get(counterId)).isEmpty();
    }

    @Test
    public void anEventForAStoredDocumentCostsOneReadAndOneWrite() {
        projection.handle(at(0, new Started(counterId)));
        var statistics = database.statistics();
        var statementsBefore = statistics.getPrepareStatementCount();

        projection.handle(at(1, new Incremented(counterId)));

        assertThat(statistics.getPrepareStatementCount() - statementsBefore).isEqualTo(2);
    }

    @Test
    public void deletingAStoredDocumentCostsOneReadAndOneWrite() {
        projection.handle(at(0, new Started(counterId)));
        var statistics = database.statistics();
        var statementsBefore = statistics.getPrepareStatementCount();

        projection.handle(at(1, new Finished(counterId)));

        assertThat(statistics.getPrepareStatementCount() - statementsBefore).isEqualTo(2);
    }

    private <Event> EventEnvelope<Event> at(long streamPosition, Event event) {
        return new EventEnvelope<>(event, Metadata.at("Counter", streamPosition));
    }
}

package io.eventdriven.core.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.projections.Versioned;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class ConditionalReadTests {
    private static final Duration deadline = Duration.ofMillis(300);

    private final AtomicInteger reads = new AtomicInteger();

    @Test
    public void aReadWithoutIfNoneMatchAnswersTheDocumentWithItsETag() {
        var response = ConditionalRead.newerThan(null, () -> read(new Document(2)), Missing::new, deadline);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"2\"");
        assertThat(response.getBody()).isEqualTo(new Document(2));
    }

    @Test
    public void aReadWaitsUntilTheViewHasAVersionNewerThanTheOneTheClientSaw() {
        var response = ConditionalRead.newerThan(
                "W/\"1\"", () -> read(new Document(reads.get() < 2 ? 1 : 2)), Missing::new, deadline);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"2\"");
        assertThat(reads.get()).isEqualTo(3);
    }

    @Test
    public void aReadAnswersNotModifiedWhenNothingNewerArrivesBeforeTheDeadline() {
        var response = ConditionalRead.newerThan("W/\"2\"", () -> read(new Document(2)), Missing::new, deadline);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"2\"");
        assertThat(response.getBody()).isNull();
    }

    @Test
    public void aReadOfADocumentThatNeverAppearsFailsAsTheCallerDecides() {
        assertThatThrownBy(() -> ConditionalRead.newerThan(null, Optional::<Document>empty, Missing::new, deadline))
                .isInstanceOf(Missing.class);
    }

    private Optional<Document> read(Document document) {
        reads.incrementAndGet();
        return Optional.of(document);
    }

    private record Document(long version) implements Versioned {
        @Override
        public long getVersion() {
            return version;
        }

        @Override
        public void setVersion(long version) {}
    }

    private static class Missing extends RuntimeException {}
}

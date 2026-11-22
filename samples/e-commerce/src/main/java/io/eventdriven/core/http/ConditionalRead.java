package io.eventdriven.core.http;

import io.eventdriven.core.projections.LongPolling;
import io.eventdriven.core.projections.Versioned;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ETag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Answers a read of a view with long polling. With {@code If-None-Match}, the client names the version it has seen,
 * and the read waits until the view has a newer one: 200 with the document and its {@code ETag}, or 304 Not Modified
 * after the deadline. Without it, the read waits until the document exists. If it never appears, the caller decides
 * what that means, for example a 404 or a view that hasn't caught up yet.
 */
public final class ConditionalRead {
    private ConditionalRead() {}

    public static <Document extends Versioned> ResponseEntity<Document> newerThan(
            @Nullable String ifNoneMatch,
            Supplier<Optional<Document>> find,
            Supplier<? extends RuntimeException> whenMissing) {
        return newerThan(ifNoneMatch, find, whenMissing, LongPolling.DEADLINE);
    }

    public static <Document extends Versioned> ResponseEntity<Document> newerThan(
            @Nullable String ifNoneMatch,
            Supplier<Optional<Document>> find,
            Supplier<? extends RuntimeException> whenMissing,
            Duration deadline) {
        var seen = versionIn(ifNoneMatch);
        var document = LongPolling.retryUntil(find, found -> found.getVersion() > seen, deadline)
                .orElseThrow(whenMissing);
        var eTag = eTagOf(document.getVersion());

        if (document.getVersion() <= seen)
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(eTag).build();

        return ResponseEntity.ok().eTag(eTag).body(document);
    }

    public static String eTagOf(long version) {
        return new ETag(String.valueOf(version), true).formattedTag();
    }

    private static long versionIn(@Nullable String ifNoneMatch) {
        if (ifNoneMatch == null) return Long.MIN_VALUE;

        return ETag.parse(ifNoneMatch).stream()
                .mapToLong(eTag -> Long.parseLong(eTag.tag()))
                .max()
                .orElse(Long.MIN_VALUE);
    }
}

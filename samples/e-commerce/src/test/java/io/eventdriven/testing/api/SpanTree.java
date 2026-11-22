package io.eventdriven.testing.api;

import io.eventdriven.testing.api.ApiE2ESpecification.ExpectedSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Matches an expected tree of spans against the spans that were recorded, wherever they were recorded. */
public final class SpanTree {
    public record RecordedSpan(
            String traceId, String spanId, String parentSpanId, String name, Map<String, String> attributes) {}

    private SpanTree() {}

    public static Optional<String> mismatchOf(ExpectedSpan expected, List<RecordedSpan> spans) {
        var named = spans.stream()
                .filter(span -> span.name().equals(expected.name()))
                .toList();

        if (named.isEmpty()) return Optional.of("No span '%s'".formatted(expected.name()));

        var candidates = named.stream().filter(span -> matches(span, expected)).toList();

        if (candidates.isEmpty())
            return Optional.of("No span '%s' with attributes %s, the spans of that name have %s"
                    .formatted(
                            expected.name(),
                            expected.attributes(),
                            named.stream().map(RecordedSpan::attributes).toList()));

        Optional<String> firstMismatch = Optional.empty();
        for (var candidate : candidates) {
            var mismatch = mismatchBelow(expected, candidate, spans);
            if (mismatch.isEmpty()) return Optional.empty();
            if (firstMismatch.isEmpty()) firstMismatch = mismatch;
        }
        return firstMismatch;
    }

    private static Optional<String> mismatchBelow(
            ExpectedSpan expected, RecordedSpan parent, List<RecordedSpan> spans) {
        for (var child : expected.children()) {
            var named = spans.stream()
                    .filter(span -> span.name().equals(child.name()) && isBelow(span, parent, spans))
                    .toList();

            if (named.isEmpty()) return Optional.of("No span '%s' below '%s'".formatted(child.name(), expected.name()));

            var candidates = named.stream().filter(span -> matches(span, child)).toList();

            if (candidates.isEmpty())
                return Optional.of("No span '%s' with attributes %s below '%s', the spans of that name have %s"
                        .formatted(
                                child.name(),
                                child.attributes(),
                                expected.name(),
                                named.stream().map(RecordedSpan::attributes).toList()));

            Optional<String> firstMismatch = Optional.empty();
            var matched = false;
            for (var candidate : candidates) {
                var mismatch = mismatchBelow(child, candidate, spans);
                if (mismatch.isEmpty()) {
                    matched = true;
                    break;
                }
                if (firstMismatch.isEmpty()) firstMismatch = mismatch;
            }
            if (!matched) return firstMismatch;
        }
        return Optional.empty();
    }

    private static boolean matches(RecordedSpan span, ExpectedSpan expected) {
        return span.attributes().entrySet().containsAll(expected.attributes().entrySet());
    }

    public static boolean isBelow(RecordedSpan span, RecordedSpan ancestor, List<RecordedSpan> spans) {
        if (!span.traceId().equals(ancestor.traceId())) return false;

        var byId = spans.stream()
                .filter(each -> each.traceId().equals(ancestor.traceId()))
                .collect(Collectors.toMap(RecordedSpan::spanId, each -> each, (first, second) -> first));
        var parentId = span.parentSpanId();

        while (byId.containsKey(parentId)) {
            if (parentId.equals(ancestor.spanId())) return true;
            parentId = byId.get(parentId).parentSpanId();
        }
        return parentId.equals(ancestor.spanId());
    }

    public static String describe(List<RecordedSpan> spans) {
        var lines = new ArrayList<String>();
        for (var span : spans) {
            lines.add("  %s (trace %s, span %s, parent %s) %s"
                    .formatted(span.name(), span.traceId(), span.spanId(), span.parentSpanId(), span.attributes()));
        }
        return String.join("\n", lines);
    }
}

package io.eventdriven.eventsversioning.testing;

import java.nio.file.Path;

class SnapshotPathResolver {
    private static final String TESTING_PACKAGE = "io.eventdriven.eventsversioning.testing";
    private static final String SOURCE_ROOT = "src/test/java";

    static Path resolve(String snapshotName) {
        var callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
            .walk(frames -> frames
                .filter(f -> !f.getClassName().startsWith(TESTING_PACKAGE))
                .filter(f -> !f.getClassName().startsWith("java."))
                .filter(f -> !f.getClassName().startsWith("jdk."))
                .filter(f -> !f.getClassName().startsWith("sun."))
                .filter(f -> !f.getClassName().startsWith("org.junit."))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot determine calling test class from stack"))
            ).getDeclaringClass();

        var packagePath = callerClass.getPackageName().replace('.', '/');
        return Path.of(SOURCE_ROOT, packagePath, snapshotName + ".approved.txt");
    }
}

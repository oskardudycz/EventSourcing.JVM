package kurrentdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TestDatabasesTests {
    private final String dockerComposeProject = "test-" + UUID.randomUUID();

    @Test
    public void usesTheKurrentDBThatDockerComposeRuns() {
        try (var kurrentDB = new KurrentDBContainer().withLabels(dockerComposeLabels("kurrentdb"))) {
            kurrentDB.start();

            assertThat(TestDatabases.kurrentDBConnectionString(dockerComposeProject))
                    .isEqualTo(kurrentDB.getConnectionString());
        }
    }

    private Map<String, String> dockerComposeLabels(String service) {
        return Map.of("com.docker.compose.project", dockerComposeProject, "com.docker.compose.service", service);
    }
}

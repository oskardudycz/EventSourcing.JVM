package io.eventdriven.ecommerce;

import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTracing
@Import({OutsideWorld.class, InMemorySpansConfiguration.class})
public class PurchaseInMemoryTests extends PurchaseTests {}

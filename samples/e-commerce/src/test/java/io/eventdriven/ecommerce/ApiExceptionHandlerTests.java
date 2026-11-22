package io.eventdriven.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.projections.ViewNotCaughtUpException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class ApiExceptionHandlerTests {
    @Test
    public void aViewThatHasNotCaughtUpYetAsksTheClientToRetryInASecond() {
        var response = new ApiExceptionHandler()
                .viewNotCaughtUp(new ViewNotCaughtUpException("Order 'o-1' is still being processed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Order 'o-1' is still being processed");
    }
}

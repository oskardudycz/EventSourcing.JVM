package io.eventdriven.ecommerce;

import io.eventdriven.core.projections.LongPolling;
import io.eventdriven.core.projections.ViewNotCaughtUpException;
import io.eventdriven.ecommerce.payments.PaymentGateway;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {
    @ExceptionHandler(ConcurrentModificationException.class)
    ProblemDetail versionConflict(ConcurrentModificationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail brokenRule(IllegalStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ViewNotCaughtUpException.class)
    ResponseEntity<ProblemDetail> viewNotCaughtUp(ViewNotCaughtUpException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(LongPolling.DEADLINE.toSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidInput(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PaymentGateway.GatewayException.class)
    ProblemDetail gatewayFailed(PaymentGateway.GatewayException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }
}

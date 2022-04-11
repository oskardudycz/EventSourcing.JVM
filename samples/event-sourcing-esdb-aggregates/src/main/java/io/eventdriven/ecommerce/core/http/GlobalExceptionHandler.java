package io.eventdriven.ecommerce.core.http;

import com.eventstore.dbclient.StreamNotFoundException;
import com.eventstore.dbclient.WrongExpectedVersionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;

import javax.persistence.EntityNotFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(value = IllegalArgumentException.class)
  public ResponseEntity<Void> illegalArgumentException(IllegalArgumentException ignored) {
    return ResponseEntity.badRequest().build();
  }

  @ExceptionHandler(value = EntityNotFoundException.class)
  public ResponseEntity<Void> entityNotFoundException(EntityNotFoundException ignored) {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(value = StreamNotFoundException.class)
  public ResponseEntity<Void> streamNotFoundException(StreamNotFoundException ignored) {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(value = IllegalStateException.class)
  public ResponseEntity<Void> illegalStateException(IllegalStateException ignored) {
    return ResponseEntity.status(HttpStatus.CONFLICT).build();
  }

  @ExceptionHandler(value = WrongExpectedVersionException.class)
  public ResponseEntity<Void> wrongExpectedVersionException(WrongExpectedVersionException ignored) {
    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
  }

  @ExceptionHandler(value = RestClientException.class)
  public ResponseEntity<Void> wrongRestClientException(RestClientException ignored) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
  }
}

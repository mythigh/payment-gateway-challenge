package com.checkout.payment.gateway.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.checkout.payment.gateway.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CommonExceptionHandlerTest {

  private final CommonExceptionHandler handler = new CommonExceptionHandler();

  @Test
  void bankCommunicationExceptionReturnsServiceUnavailable() {
    ResponseEntity<ErrorResponse> response = handler.handleBankCommunicationException(
        new BankCommunicationException("Bank unavailable"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertEquals("Unable to process payment with the acquiring bank",
        response.getBody().getMessage());
  }

  @Test
  void bankCommunicationExceptionWithBadRequestStatusReturnsInternalServerError() {
    ResponseEntity<ErrorResponse> response = handler.handleBankCommunicationException(
        new BankCommunicationException("Bank rejected the payment", HttpStatus.BAD_REQUEST.value()));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("Unable to process payment with the acquiring bank",
        response.getBody().getMessage());
  }

  @Test
  void bankCommunicationExceptionWithOther4xxStatusReturnsInternalServerError() {
    ResponseEntity<ErrorResponse> response = handler.handleBankCommunicationException(
        new BankCommunicationException("Bank rejected the payment", HttpStatus.UNAUTHORIZED.value()));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  void unexpectedExceptionReturnsInternalServerError() {
    ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(
        new IllegalStateException("Unexpected failure"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("Unable to process payment", response.getBody().getMessage());
  }

  @Test
  void eventProcessingExceptionReturnsNotFound() {
    ResponseEntity<ErrorResponse> response = handler.handleException(
        new EventProcessingException("Invalid ID"));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("Page not found", response.getBody().getMessage());
  }
}

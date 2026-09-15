package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.info("event=payment_not_found reason={}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(BankCommunicationException.class)
  public ResponseEntity<ErrorResponse> handleBankCommunicationException(
      BankCommunicationException ex) {
    boolean bankClientError = ex.getStatusCode() >= HttpStatus.BAD_REQUEST.value()
      && ex.getStatusCode() < HttpStatus.INTERNAL_SERVER_ERROR.value();
    HttpStatus status = bankClientError ? HttpStatus.INTERNAL_SERVER_ERROR
      : HttpStatus.SERVICE_UNAVAILABLE;

    LOG.error("event=bank_request_failed failure_type={} upstream_status={}",
      bankClientError ? "bank_contract_error" : "bank_unavailable", ex.getStatusCode());
    return new ResponseEntity<>(
        new ErrorResponse("Unable to process payment with the acquiring bank"),
        status);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMalformedPaymentRequest(
      HttpMessageNotReadableException ex) {
    LOG.info("event=payment_request_rejected reason=malformed_request");
    return new ResponseEntity<>(new ErrorResponse("Malformed payment request"),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
    LOG.error("event=unexpected_system_error", ex);
    return new ResponseEntity<>(new ErrorResponse("Unable to process payment"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
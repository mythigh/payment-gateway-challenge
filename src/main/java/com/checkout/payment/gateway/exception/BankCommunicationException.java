package com.checkout.payment.gateway.exception;

import org.springframework.http.HttpStatus;

public class BankCommunicationException extends RuntimeException {

  private final int statusCode;

  public BankCommunicationException(String message, Throwable cause) {
    this(message, HttpStatus.SERVICE_UNAVAILABLE.value(), cause);
  }

  public BankCommunicationException(String message) {
    this(message, HttpStatus.SERVICE_UNAVAILABLE.value());
  }

  public BankCommunicationException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  public BankCommunicationException(String message, int statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
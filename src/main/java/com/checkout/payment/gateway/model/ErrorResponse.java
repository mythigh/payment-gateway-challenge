package com.checkout.payment.gateway.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "API error response")
public class ErrorResponse {
  @Schema(description = "Human-readable error message", example = "Malformed payment request")
  private final String message;

  public ErrorResponse(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "ErrorResponse{" +
        "message='" + message + '\'' +
        '}';
  }
}

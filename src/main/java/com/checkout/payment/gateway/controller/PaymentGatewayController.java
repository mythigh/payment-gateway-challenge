package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
@RequestMapping("/api/v1")
@Tag(name = "Payments", description = "Create and retrieve payment records")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @GetMapping("/payment/{id}")
    @Operation(summary = "Retrieve a payment", description = "Retrieves a payment by its gateway UUID.")
    @Parameter(name = "X-Correlation-ID", in = ParameterIn.HEADER, required = false,
      description = "Optional request correlation ID. A UUID is generated when omitted or invalid.")
    @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Payment found",
        headers = @Header(name = "X-Correlation-ID", description = "Request correlation ID"),
        content = @Content(schema = @Schema(implementation = PostPaymentResponse.class))),
      @ApiResponse(responseCode = "404", description = "Payment not found",
        headers = @Header(name = "X-Correlation-ID", description = "Request correlation ID"),
          content = @Content(schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"message\":\"Page not found\"}")))
    })
    public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(
      @Parameter(description = "Gateway payment UUID", required = true) @PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }

  @PostMapping("/payments")
    @Operation(summary = "Process a payment",
      description = "Validates the request and submits it to the acquiring bank.")
    @Parameter(name = "X-Correlation-ID", in = ParameterIn.HEADER, required = false,
      description = "Optional request correlation ID. A UUID is generated when omitted or invalid.")
    @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Payment processed",
        headers = @Header(name = "X-Correlation-ID", description = "Request correlation ID"),
        content = @Content(schema = @Schema(implementation = PostPaymentResponse.class))),
      @ApiResponse(responseCode = "400", description = "Malformed JSON request",
        headers = @Header(name = "X-Correlation-ID", description = "Request correlation ID"),
          content = @Content(schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"message\":\"Malformed payment request\"}"))),
      @ApiResponse(responseCode = "500", description = "Bank contract error or unexpected gateway error",
        headers = @Header(name = "X-Correlation-ID", description = "Request correlation ID"),
          content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
            @ExampleObject(name = "Bank contract error",
              value = "{\"message\":\"Unable to process payment with the acquiring bank\"}"),
            @ExampleObject(name = "Unexpected gateway error",
              value = "{\"message\":\"Unable to process payment\"}")
          })),
      @ApiResponse(responseCode = "503", description = "Acquiring bank unavailable",
        headers = @Header(name = "X-Correlation-ID", description = "Request correlation ID"),
          content = @Content(schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(
              value = "{\"message\":\"Unable to process payment with the acquiring bank\"}")))
    })
  public ResponseEntity<PostPaymentResponse> processPayment(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
        description = "Payment details") @RequestBody PostPaymentRequest paymentRequest) {
    return new ResponseEntity<>(paymentGatewayService.processPayment(paymentRequest),
        HttpStatus.CREATED);
  }
}

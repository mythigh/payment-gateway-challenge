package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import java.util.List;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

    private final PaymentRepository paymentsRepository;
  private final PaymentRequestValidator paymentRequestValidator;
    private final BankPaymentGateway bankClient;
    private final MeterRegistry meterRegistry;

    public PaymentGatewayService(PaymentRepository paymentsRepository,
        PaymentRequestValidator paymentRequestValidator, BankPaymentGateway bankClient,
        MeterRegistry meterRegistry) {
    this.paymentsRepository = paymentsRepository;
    this.paymentRequestValidator = paymentRequestValidator;
    this.bankClient = bankClient;
      this.meterRegistry = meterRegistry;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id)
        .map(payment -> {
          meterRegistry.counter("payments.retrieved", "result", "found").increment();
          return payment;
        })
        .orElseThrow(() -> {
          meterRegistry.counter("payments.retrieved", "result", "not_found").increment();
          return new EventProcessingException("Invalid ID");
        });
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    long processingStartedAt = System.nanoTime();
    LOG.info("event=payment_received amount={} currency={} card_last_four={}",
        paymentRequest == null ? null : paymentRequest.getAmount(),
        paymentRequest == null ? null : paymentRequest.getCurrency(),
        paymentRequest == null ? null : extractLastFourDigits(paymentRequest.getCardNumber()));

    List<String> validationErrors = paymentRequestValidator.validate(paymentRequest);
    if (!validationErrors.isEmpty()) {
      PostPaymentResponse rejectedPayment = createPaymentResponse(paymentRequest,
          PaymentStatus.REJECTED);
      LOG.info("event=payment_processed payment_id={} status={} duration_ms={}"
              + " validation_error_count={}", rejectedPayment.getId(), rejectedPayment.getStatus(),
          elapsedMilliseconds(processingStartedAt), validationErrors.size());
      LOG.debug("event=payment_validation_errors payment_id={} validation_errors={}",
          rejectedPayment.getId(), validationErrors);
      recordPaymentProcessed(rejectedPayment.getStatus(), processingStartedAt);
      return rejectedPayment;
    }

    try {
      BankPaymentResponse bankResponse = bankClient.makePayment(paymentRequest);
      PostPaymentResponse payment = createPaymentResponse(paymentRequest,
          bankResponse.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
      paymentsRepository.add(payment);
      LOG.info("event=payment_processed payment_id={} status={} duration_ms={}", payment.getId(),
          payment.getStatus(), elapsedMilliseconds(processingStartedAt));
        recordPaymentProcessed(payment.getStatus(), processingStartedAt);
      return payment;
    } catch (BankCommunicationException exception) {
      LOG.error("event=payment_processing_failed failure_type=bank_error duration_ms={}",
          elapsedMilliseconds(processingStartedAt));
        meterRegistry.counter("payments.processing.failures", "failure_type", "bank_error")
          .increment();
        Timer.builder("payments.processing.duration")
          .tag("outcome", "bank_error")
          .register(meterRegistry)
          .record(System.nanoTime() - processingStartedAt, java.util.concurrent.TimeUnit.NANOSECONDS);
      throw exception;
    }
  }

  private PostPaymentResponse createPaymentResponse(PostPaymentRequest paymentRequest,
      PaymentStatus status) {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setStatus(status);

    if (paymentRequest != null) {
      payment.setCardNumberLastFour(extractLastFourDigits(paymentRequest.getCardNumber()));
      payment.setExpiryMonth(paymentRequest.getExpiryMonth());
      payment.setExpiryYear(paymentRequest.getExpiryYear());
      payment.setCurrency(paymentRequest.getCurrency());
      payment.setAmount(paymentRequest.getAmount());
    }

    return payment;
  }

  private String extractLastFourDigits(String cardNumber) {
    if (cardNumber == null || cardNumber.length() < 4) {
      return null;
    }

    return cardNumber.substring(cardNumber.length() - 4);
  }

  private long elapsedMilliseconds(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private void recordPaymentProcessed(PaymentStatus status, long processingStartedAt) {
    String outcome = status.name().toLowerCase();
    meterRegistry.counter("payments.processed", "outcome", outcome).increment();
    Timer.builder("payments.processing.duration")
        .tag("outcome", outcome)
        .register(meterRegistry)
        .record(System.nanoTime() - processingStartedAt, java.util.concurrent.TimeUnit.NANOSECONDS);
  }
}
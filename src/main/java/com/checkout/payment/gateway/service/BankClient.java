package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankClient implements BankPaymentGateway {

  private static final Logger LOG = LoggerFactory.getLogger(BankClient.class);

  private final RestTemplate restTemplate;
  private final String bankUrl;
  private final MeterRegistry meterRegistry;

  public BankClient(RestTemplate restTemplate,
      @Value("${bank.url}") String bankUrl, MeterRegistry meterRegistry) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public BankPaymentResponse makePayment(PostPaymentRequest paymentRequest) {
    long bankRequestStartedAt = System.nanoTime();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    Map<String, Object> requestBody = Map.of(
        "card_number", paymentRequest.getCardNumber(),
      "expiry_date", formatExpiryDate(paymentRequest.getExpiryDate()),
        "currency", paymentRequest.getCurrency(),
        "amount", paymentRequest.getAmount(),
        "cvv", paymentRequest.getCvv());

    try {
      ResponseEntity<BankPaymentResponse> response = restTemplate.postForEntity(
          bankUrl + "/payments",
          new HttpEntity<>(requestBody, headers),
          BankPaymentResponse.class);

      if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
        throw new BankCommunicationException(
            "Bank returned an invalid payment response",
            response.getStatusCode().value());
      }

      BankPaymentResponse bankResponse = response.getBody();
      if (bankResponse.authorized() == null) {
        throw new BankCommunicationException(
            "Bank response missing required 'authorized' field",
            org.springframework.http.HttpStatus.BAD_REQUEST.value());
      }

        LOG.info("event=bank_authorization_completed result={} duration_ms={}",
          bankResponse.authorized() ? "authorized" : "declined",
          elapsedMilliseconds(bankRequestStartedAt));
        recordBankRequest(bankResponse.authorized() ? "authorized" : "declined", bankRequestStartedAt);
      return bankResponse;
    } catch (BankCommunicationException exception) {
        LOG.error("event=bank_authorization_failed failure_type=bank_response_error"
            + " upstream_status={} duration_ms={}", exception.getStatusCode(),
          elapsedMilliseconds(bankRequestStartedAt));
            recordBankFailure("bank_response_error", bankRequestStartedAt);
      throw exception;
    } catch (RestClientResponseException exception) {
        BankCommunicationException bankException = new BankCommunicationException(
          "Acquiring bank returned an error response", exception.getRawStatusCode(), exception);
        LOG.error("event=bank_authorization_failed failure_type=bank_response_error"
            + " upstream_status={} duration_ms={}", bankException.getStatusCode(),
          elapsedMilliseconds(bankRequestStartedAt));
          recordBankFailure("bank_response_error", bankRequestStartedAt);
        throw bankException;
    } catch (RestClientException exception) {
        BankCommunicationException bankException = new BankCommunicationException(
          "Unable to communicate with the acquiring bank", exception);
        LOG.error("event=bank_authorization_failed failure_type=bank_unavailable"
            + " upstream_status={} duration_ms={}", bankException.getStatusCode(),
          elapsedMilliseconds(bankRequestStartedAt));
          recordBankFailure("bank_unavailable", bankRequestStartedAt);
        throw bankException;
    }
  }

  private String formatExpiryDate(String expiryDate) {
    String[] parts = expiryDate.split("/");
    return String.format("%02d/%04d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
  }

  private long elapsedMilliseconds(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private void recordBankRequest(String outcome, long bankRequestStartedAt) {
    meterRegistry.counter("bank.authorization.requests", "outcome", outcome).increment();
    Timer.builder("bank.authorization.duration")
        .tag("outcome", outcome)
        .register(meterRegistry)
        .record(System.nanoTime() - bankRequestStartedAt, TimeUnit.NANOSECONDS);
  }

  private void recordBankFailure(String failureType, long bankRequestStartedAt) {
    meterRegistry.counter("bank.authorization.failures", "failure_type", failureType).increment();
    Timer.builder("bank.authorization.duration")
        .tag("outcome", "failure")
        .register(meterRegistry)
        .record(System.nanoTime() - bankRequestStartedAt, TimeUnit.NANOSECONDS);
  }
}
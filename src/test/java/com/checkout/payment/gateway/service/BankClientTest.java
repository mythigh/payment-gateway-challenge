package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpServerErrorException;

@ExtendWith(MockitoExtension.class)
class BankClientTest {

  @Mock
  private RestTemplate restTemplate;

  private BankClient bankClient;

  @BeforeEach
  void setUp() {
    bankClient = new BankClient(restTemplate, "http://bank.example", new SimpleMeterRegistry());
  }

  @Test
  void sendsExpectedPayloadAndParsesAuthorizedResponse() {
    PostPaymentRequest request = paymentRequest();
    BankPaymentResponse expectedResponse = new BankPaymentResponse(true, "auth-code");
    when(restTemplate.postForEntity(
        eq("http://bank.example/payments"),
        any(HttpEntity.class),
        eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok(expectedResponse));

    BankPaymentResponse response = bankClient.makePayment(request);

    assertEquals(expectedResponse, response);

    ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate).postForEntity(
        eq("http://bank.example/payments"),
        entityCaptor.capture(),
        eq(BankPaymentResponse.class));

    HttpEntity<?> requestEntity = entityCaptor.getValue();
    assertEquals(MediaType.APPLICATION_JSON, requestEntity.getHeaders().getContentType());
    assertEquals(Map.of(
        "card_number", "4111111111111111",
        "expiry_date", "04/2027",
        "currency", "GBP",
        "amount", 100,
        "cvv", "123"), requestEntity.getBody());
  }

  @Test
  void badRequestBankResponseCarriesStatusCode() {
    when(restTemplate.postForEntity(
        eq("http://bank.example/payments"),
        any(HttpEntity.class),
        eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());

    BankCommunicationException exception = assertThrows(BankCommunicationException.class,
        () -> bankClient.makePayment(paymentRequest()));

    assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode());
  }

  @Test
  void badRequestExceptionFromBankCarriesStatusCode() {
    when(restTemplate.postForEntity(
        eq("http://bank.example/payments"),
        any(HttpEntity.class),
        eq(BankPaymentResponse.class)))
        .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
            org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

    BankCommunicationException exception = assertThrows(BankCommunicationException.class,
        () -> bankClient.makePayment(paymentRequest()));

    assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode());
  }

  @Test
  void serviceUnavailableExceptionFromBankCarriesStatusCode() {
    when(restTemplate.postForEntity(
        eq("http://bank.example/payments"),
        any(HttpEntity.class),
        eq(BankPaymentResponse.class)))
        .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
            "Service Unavailable", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

    BankCommunicationException exception = assertThrows(BankCommunicationException.class,
        () -> bankClient.makePayment(paymentRequest()));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getStatusCode());
  }

  @Test
  void missingAuthorizedFieldInBankResponseBecomesCommunicationException() {
    when(restTemplate.postForEntity(
        eq("http://bank.example/payments"),
        any(HttpEntity.class),
        eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok().body(new BankPaymentResponse(null, "auth-code")));

    BankCommunicationException exception = assertThrows(BankCommunicationException.class,
        () -> bankClient.makePayment(paymentRequest()));

    assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode());
  }

  @Test
  void emptyBankResponseBecomesCommunicationException() {
    when(restTemplate.postForEntity(
        eq("http://bank.example/payments"),
        any(HttpEntity.class),
        eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok().build());

    assertThrows(BankCommunicationException.class,
        () -> bankClient.makePayment(paymentRequest()));
  }

  @Test
  void transportFailureBecomesCommunicationException() {
    when(restTemplate.postForEntity(
        eq("http://bank.example/payments"),
        any(HttpEntity.class),
        eq(BankPaymentResponse.class)))
        .thenThrow(new RestClientException("Connection refused"));

    assertThrows(BankCommunicationException.class,
        () -> bankClient.makePayment(paymentRequest()));
  }

  private PostPaymentRequest paymentRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("4111111111111111");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}

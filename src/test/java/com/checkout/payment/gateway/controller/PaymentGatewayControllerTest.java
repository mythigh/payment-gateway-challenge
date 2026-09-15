package com.checkout.payment.gateway.controller;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.service.BankPaymentGateway;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  @MockBean
  BankPaymentGateway bankClient;

  @Test
  void generatedCorrelationIdIsReturnedWhenRequestDoesNotSupplyOne() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/v1/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(result -> UUID.fromString(
            result.getResponse().getHeader("X-Correlation-ID")));
  }

  @Test
  void suppliedCorrelationIdIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/v1/payment/" + UUID.randomUUID())
            .header("X-Correlation-ID", "checkout-request-123"))
        .andExpect(status().isNotFound())
        .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(
            "checkout-request-123", result.getResponse().getHeader("X-Correlation-ID")));
  }

  @Test
  void invalidCorrelationIdIsReplacedWithGeneratedId() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/v1/payment/" + UUID.randomUUID())
            .header("X-Correlation-ID", "invalid correlation id"))
        .andExpect(status().isNotFound())
        .andExpect(result -> UUID.fromString(
            result.getResponse().getHeader("X-Correlation-ID")));
  }

  @Test
  void healthEndpointIsAvailable() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void paymentOutcomeMetricIsAvailableAfterPaymentProcessing() throws Exception {
    when(bankClient.makePayment(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));

    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson("4111111111111111")))
        .andExpect(status().isCreated());

    mvc.perform(MockMvcRequestBuilders.get("/actuator/metrics/payments.processed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("payments.processed"));
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour("4321");

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/api/v1/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/v1/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void whenPaymentIsAuthorizedThen201AndAuthorizedResponseAreReturned() throws Exception {
    when(bankClient.makePayment(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));

    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson("4111111111111111")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("1111"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  @Test
  void whenPaymentIsDeclinedThen201AndDeclinedResponseAreReturned() throws Exception {
    when(bankClient.makePayment(any())).thenReturn(new BankPaymentResponse(false, ""));

    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson("4111111111111112")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("1112"));
  }

  @Test
  void whenPaymentIsInvalidThenRejectedResponseIsReturnedWithoutBankCall() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson("411")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.cardNumberLastFour").doesNotExist());
  }

        @Test
        void whenPaymentJsonIsMalformedThen400ErrorResponseIsReturned() throws Exception {
          mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"card_number\":\"4111111111111111\""))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Malformed payment request"));
        }

        @Test
        void whenPaymentFieldHasWrongJsonTypeThen400ErrorResponseIsReturned() throws Exception {
          mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"amount\":\"not-an-integer\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Malformed payment request"));
        }

  @Test
  void whenBankIsUnavailableThen503ErrorResponseIsReturned() throws Exception {
    when(bankClient.makePayment(any()))
        .thenThrow(new BankCommunicationException("Bank unavailable"));

    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson("4111111111111111")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message")
            .value("Unable to process payment with the acquiring bank"));
  }

  private String validPaymentJson(String cardNumber) {
    return "{"
        + "\"card_number\":\"" + cardNumber + "\","
        + "\"expiry_month\":12,"
        + "\"expiry_year\":2027,"
        + "\"currency\":\"GBP\","
        + "\"amount\":100,"
        + "\"cvv\":\"123\""
        + "}";
  }
}

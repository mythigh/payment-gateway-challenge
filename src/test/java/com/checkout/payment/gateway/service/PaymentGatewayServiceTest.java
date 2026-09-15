package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.service.BankPaymentGateway;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentRepository paymentsRepository;

  @Mock
  private BankPaymentGateway bankClient;

  private PaymentGatewayService paymentGatewayService;

  @BeforeEach
  void setUp() {
    paymentGatewayService = new PaymentGatewayService(
      paymentsRepository, new PaymentRequestValidator(), bankClient, new SimpleMeterRegistry());
  }

  @Test
  void invalidPaymentIsRejectedWithoutCallingBankOrSaving() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("123");

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertEquals(PaymentStatus.REJECTED, response.getStatus());
    assertEquals(null, response.getCardNumberLastFour());
    assertEquals(request.getExpiryMonth(), response.getExpiryMonth());
    assertEquals(request.getExpiryYear(), response.getExpiryYear());
    assertEquals(request.getCurrency(), response.getCurrency());
    assertEquals(request.getAmount(), response.getAmount());
    verify(bankClient, never()).makePayment(any());
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void authorizedPaymentIsMappedAndSaved() {
    PostPaymentRequest request = validRequest();
    when(bankClient.makePayment(request)).thenReturn(new BankPaymentResponse(true, "auth-code"));

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
    assertEquals("0001", response.getCardNumberLastFour());
    verifySavedPayment(response);
  }

  @Test
  void declinedPaymentIsMappedAndSaved() {
    PostPaymentRequest request = validRequest();
    when(bankClient.makePayment(request)).thenReturn(new BankPaymentResponse(false, ""));

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertEquals(PaymentStatus.DECLINED, response.getStatus());
    assertEquals("0001", response.getCardNumberLastFour());
    verifySavedPayment(response);
  }

  @Test
  void bankCommunicationFailureIsPropagatedWithoutSaving() {
    PostPaymentRequest request = validRequest();
    BankCommunicationException exception = new BankCommunicationException("Bank unavailable");
    when(bankClient.makePayment(request)).thenThrow(exception);

    BankCommunicationException thrown = assertThrows(BankCommunicationException.class,
        () -> paymentGatewayService.processPayment(request));

    assertEquals(exception, thrown);
    verify(paymentsRepository, never()).add(any());
  }

  private PostPaymentRequest validRequest() {
    YearMonth expiry = YearMonth.now().plusMonths(1);
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("4111111111110001");
    request.setExpiryMonth(expiry.getMonthValue());
    request.setExpiryYear(expiry.getYear());
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  private void verifySavedPayment(PostPaymentResponse expectedPayment) {
    ArgumentCaptor<PostPaymentResponse> paymentCaptor = ArgumentCaptor.forClass(
        PostPaymentResponse.class);
    verify(paymentsRepository).add(paymentCaptor.capture());
    PostPaymentResponse savedPayment = paymentCaptor.getValue();
    assertEquals(expectedPayment.getId(), savedPayment.getId());
    assertEquals(expectedPayment.getStatus(), savedPayment.getStatus());
    assertEquals(expectedPayment.getCardNumberLastFour(), savedPayment.getCardNumberLastFour());
    assertEquals(expectedPayment.getAmount(), savedPayment.getAmount());
  }
}

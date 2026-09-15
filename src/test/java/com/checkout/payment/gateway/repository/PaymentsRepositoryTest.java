package com.checkout.payment.gateway.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentsRepositoryTest {

  private final PaymentsRepository paymentsRepository = new PaymentsRepository();

  @Test
  void storedPaymentCanBeRetrievedById() {
    PostPaymentResponse payment = payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED);

    paymentsRepository.add(payment);

    assertEquals(payment, paymentsRepository.get(payment.getId()).orElseThrow());
  }

  @Test
  void unknownPaymentIdReturnsEmpty() {
    assertTrue(paymentsRepository.get(UUID.randomUUID()).isEmpty());
  }

  @Test
  void paymentWithExistingIdReplacesStoredPayment() {
    UUID paymentId = UUID.randomUUID();
    paymentsRepository.add(payment(paymentId, PaymentStatus.AUTHORIZED));
    PostPaymentResponse replacement = payment(paymentId, PaymentStatus.DECLINED);

    paymentsRepository.add(replacement);

    assertEquals(replacement, paymentsRepository.get(paymentId).orElseThrow());
  }

  private PostPaymentResponse payment(UUID id, PaymentStatus status) {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(id);
    payment.setStatus(status);
    return payment;
  }
}
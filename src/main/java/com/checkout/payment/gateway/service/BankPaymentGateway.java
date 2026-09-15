package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;

public interface BankPaymentGateway {
  BankPaymentResponse makePayment(PostPaymentRequest paymentRequest);
}
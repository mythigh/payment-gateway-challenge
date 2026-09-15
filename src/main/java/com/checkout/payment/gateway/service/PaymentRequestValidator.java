package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestValidator {

  private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\d{14,19}");
  private static final Pattern CVV_PATTERN = Pattern.compile("\\d{3,4}");
  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "GBP", "EUR");

  public List<String> validate(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    if (request == null) {
      errors.add("Payment request is required");
      return errors;
    }

    validateCardNumber(request.getCardNumber(), errors);
    validateExpiry(request.getExpiryMonth(), request.getExpiryYear(), errors);
    validateCurrency(request.getCurrency(), errors);
    validateAmount(request.getAmount(), errors);
    validateCvv(request.getCvv(), errors);

    return errors;
  }

  private void validateCardNumber(String cardNumber, List<String> errors) {
    if (cardNumber == null || !CARD_NUMBER_PATTERN.matcher(cardNumber).matches()) {
      errors.add("Card number must contain 14 to 19 numeric digits");
    }
  }

  private void validateExpiry(int expiryMonth, int expiryYear, List<String> errors) {
    if (expiryMonth < 1 || expiryMonth > 12) {
      errors.add("Expiry month must be between 1 and 12");
      return;
    }

    if (!YearMonth.of(expiryYear, expiryMonth).isAfter(YearMonth.now())) {
      errors.add("Expiry date must be in the future");
    }
  }

  private void validateCurrency(String currency, List<String> errors) {
    if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) {
      errors.add("Currency must be USD, GBP, or EUR");
    }
  }

  private void validateAmount(int amount, List<String> errors) {
    if (amount <= 0) {
      errors.add("Amount must be greater than zero");
    }
  }

  private void validateCvv(String cvv, List<String> errors) {
    if (cvv == null || !CVV_PATTERN.matcher(cvv).matches()) {
      errors.add("CVV must contain 3 or 4 numeric digits");
    }
  }
}
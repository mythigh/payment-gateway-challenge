package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentRequestValidatorTest {

  private PaymentRequestValidator validator;
  private PostPaymentRequest validRequest;

  @BeforeEach
  void setUp() {
    validator = new PaymentRequestValidator();
    validRequest = new PostPaymentRequest();
    validRequest.setCardNumber("41111111111111");
    validRequest.setExpiryMonth(YearMonth.now().plusMonths(1).getMonthValue());
    validRequest.setExpiryYear(YearMonth.now().plusMonths(1).getYear());
    validRequest.setCurrency("USD");
    validRequest.setAmount(1);
    validRequest.setCvv("123");
  }

  @Test
  void validRequestHasNoErrors() {
    assertTrue(validator.validate(validRequest).isEmpty());
  }

  @Test
  void cardNumbersWith14And19DigitsAreValid() {
    validRequest.setCardNumber("12345678901234");
    assertTrue(validator.validate(validRequest).isEmpty());

    validRequest.setCardNumber("1234567890123456789");
    assertTrue(validator.validate(validRequest).isEmpty());
  }

  @Test
  void invalidCardNumbersAreRejected() {
    assertHasError("1234567890123", "Card number must contain 14 to 19 numeric digits");

    validRequest.setCardNumber("12345678901234567890");
    assertHasError("12345678901234567890", "Card number must contain 14 to 19 numeric digits");

    validRequest.setCardNumber("1234567890123A");
    assertHasError("1234567890123A", "Card number must contain 14 to 19 numeric digits");
  }

  @Test
  void invalidExpiryMonthIsRejected() {
    validRequest.setExpiryMonth(0);
    assertHasError("Expiry month must be between 1 and 12");

    validRequest.setExpiryMonth(13);
    assertHasError("Expiry month must be between 1 and 12");
  }

  @Test
  void expiryMonthsOneAndTwelveAreValidWhenInTheFuture() {
    validRequest.setExpiryMonth(1);
    validRequest.setExpiryYear(futureYearForMonth(1));
    assertTrue(validator.validate(validRequest).isEmpty());

    validRequest.setExpiryMonth(12);
    validRequest.setExpiryYear(futureYearForMonth(12));
    assertTrue(validator.validate(validRequest).isEmpty());
  }

  @Test
  void expiredExpiryDateIsRejected() {
    YearMonth expiredDate = YearMonth.now().minusMonths(1);
    validRequest.setExpiryMonth(expiredDate.getMonthValue());
    validRequest.setExpiryYear(expiredDate.getYear());

    assertHasError("Expiry date must be in the future");
  }

  @Test
  void expiryDateEqualToCurrentMonthIsRejected() {
    YearMonth currentDate = YearMonth.now();
    validRequest.setExpiryMonth(currentDate.getMonthValue());
    validRequest.setExpiryYear(currentDate.getYear());

    assertHasError("Expiry date must be in the future");
  }

  @Test
  void unsupportedOrMissingCurrencyIsRejected() {
    validRequest.setCurrency("CAD");
    assertHasError("Currency must be USD, GBP, or EUR");

    validRequest.setCurrency(null);
    assertHasError("Currency must be USD, GBP, or EUR");
  }

  @Test
  void nonPositiveAmountIsRejected() {
    validRequest.setAmount(0);
    assertHasError("Amount must be greater than zero");

    validRequest.setAmount(-1);
    assertHasError("Amount must be greater than zero");
  }

  @Test
  void threeAndFourDigitCvvsAreValid() {
    validRequest.setCvv("123");
    assertTrue(validator.validate(validRequest).isEmpty());

    validRequest.setCvv("1234");
    assertTrue(validator.validate(validRequest).isEmpty());
  }

  @Test
  void invalidOrMissingCvvIsRejected() {
    validRequest.setCvv("12");
    assertHasError("CVV must contain 3 or 4 numeric digits");

    validRequest.setCvv("12345");
    assertHasError("CVV must contain 3 or 4 numeric digits");

    validRequest.setCvv("12A");
    assertHasError("CVV must contain 3 or 4 numeric digits");

    validRequest.setCvv(null);
    assertHasError("CVV must contain 3 or 4 numeric digits");
  }

  @Test
  void missingCardNumberIsRejected() {
    validRequest.setCardNumber(null);

    assertHasError("Card number must contain 14 to 19 numeric digits");
  }

  @Test
  void nullRequestIsRejectedWithoutThrowing() {
    List<String> errors = assertDoesNotThrow(() -> validator.validate(null));

    assertEquals(List.of("Payment request is required"), errors);
  }

  private void assertHasError(String expectedError) {
    List<String> errors = validator.validate(validRequest);

    assertFalse(errors.isEmpty());
    assertTrue(errors.contains(expectedError));
  }

  private void assertHasError(String cardNumber, String expectedError) {
    validRequest.setCardNumber(cardNumber);
    assertHasError(expectedError);
  }

  private int futureYearForMonth(int month) {
    YearMonth currentDate = YearMonth.now();
    return month > currentDate.getMonthValue() ? currentDate.getYear() : currentDate.getYear() + 1;
  }
}

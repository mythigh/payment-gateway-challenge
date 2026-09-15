package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "Payment details supplied by the caller")
public class PostPaymentRequest implements Serializable {

  @JsonProperty("card_number")
  @Schema(description = "Card number", example = "4111111111111111", minLength = 14,
      maxLength = 19, pattern = "\\d{14,19}")
  private String cardNumber;
  @JsonProperty("expiry_month")
  @Schema(description = "Card expiry month", example = "12", minimum = "1", maximum = "12")
  private int expiryMonth;
  @JsonProperty("expiry_year")
  @Schema(description = "Card expiry year", example = "2027")
  private int expiryYear;
  @Schema(description = "ISO currency code", example = "GBP", allowableValues = {"USD", "GBP", "EUR"})
  private String currency;
  @Schema(description = "Amount in minor currency units", example = "100", minimum = "1")
  private int amount;
  @Schema(description = "Card verification value", example = "123", pattern = "\\d{3,4}")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  @JsonIgnore
  public String getExpiryDate() {
    return String.format("%d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    String maskedCard = (cardNumber != null && cardNumber.length() >= 4)
      ? "****" + cardNumber.substring(cardNumber.length() - 4)
        : "invalid";

    return "PostPaymentRequest{" +
        "cardNumber=" + maskedCard +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv='[REDACTED]'" +
        '}';
  }

}

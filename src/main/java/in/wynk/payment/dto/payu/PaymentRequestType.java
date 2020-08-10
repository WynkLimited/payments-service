package in.wynk.payment.dto.payu;

public enum PaymentRequestType {
  DEFAULT,
  SUBSCRIBE,
  RENEW_SUBSCRIPTION,
  REFUND;

  public static PaymentRequestType getByName(String reqType) {
    try {
      return PaymentRequestType.valueOf(reqType);
    } catch (Exception ex) {
      return DEFAULT;
    }
  }
}

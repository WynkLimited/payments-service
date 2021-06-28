package in.wynk.payment.core.dao.entity;

public interface IPaymentDetails {
    String getCouponId();
    String getPaymentCode();
    String getPaymentMode();
    String getMerchantName();
    boolean isTrialOpted();
    boolean isAutoRenew();
}

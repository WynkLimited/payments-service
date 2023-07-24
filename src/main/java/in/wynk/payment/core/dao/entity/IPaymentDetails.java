package in.wynk.payment.core.dao.entity;

import java.io.Serializable;

public interface IPaymentDetails extends Serializable {

    boolean isTrialOpted();

    boolean isPennyDrop();

    boolean isAutoRenew();

    String getCouponId();

    String getPaymentId();

    String getPaymentMode();

    String getMerchantName();

}
package in.wynk.payment.core.dao.entity;

import in.wynk.payment.dto.request.common.*;

import java.io.Serializable;

public interface IPaymentDetails extends Serializable {

    boolean isTrialOpted();

    boolean isAutoRenew();

    String getCouponId();

    String getPaymentId();

    String getPaymentMode();

    String getMerchantName();

    boolean isIntent();

}
package in.wynk.payment.validations;

import in.wynk.common.validations.IBaseRequest;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.IProductDetails;

public interface ICouponValidatorRequest extends IBaseRequest {

    String getMsisdn();

    String getService();

    String getCouponCode();

    PaymentCode getPaymentCode();

    IProductDetails getProductDetails();

}
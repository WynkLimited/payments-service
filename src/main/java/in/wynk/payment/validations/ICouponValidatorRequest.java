package in.wynk.payment.validations;

import in.wynk.common.validations.IBaseRequest;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.PaymentGateway;

public interface ICouponValidatorRequest extends IBaseRequest {

    String getMsisdn();

    String getService();

    String getCouponCode();

    PaymentGateway getPaymentCode();

    IProductDetails getProductDetails();

}
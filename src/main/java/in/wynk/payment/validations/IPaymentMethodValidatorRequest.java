package in.wynk.payment.validations;

import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.common.validations.IBaseRequest;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;

public interface IPaymentMethodValidatorRequest extends IBaseRequest {

    int getBuildNo();

    String getOs();

    String getAppId();

    String getMsisdn();

    String getService();

    String getPaymentId();

    default String getCouponCode() {
        return null;
    };

    default String getCountryCode() {
        return "IN";
    }

    default String getSi(){
        return null;
    }

    IProductDetails getProductDetails();

    ClientDetails getClientDetails();

    IPaymentDetails getPaymentDetails();

}
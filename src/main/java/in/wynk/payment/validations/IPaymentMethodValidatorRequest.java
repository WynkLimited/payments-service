package in.wynk.payment.validations;

import in.wynk.common.validations.IBaseRequest;
import in.wynk.payment.core.dao.entity.IProductDetails;

public interface IPaymentMethodValidatorRequest extends IBaseRequest {

    int getBuildNo();

    String getOs();

    String getAppId();

    String getMsisdn();

    String getService();

    String getPaymentId();

    String getCouponCode();

    String getCountryCode();

    default String getSi(){
        return null;
    }

    IProductDetails getProductDetails();

}
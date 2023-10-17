package in.wynk.payment.dto;

import in.wynk.common.dto.IGeoLocation;
import in.wynk.common.dto.IMiscellaneousDetails;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;

public interface IPaymentOptionsRequest {

    String getCouponId();

    IAppDetails getAppDetails();

    IUserDetails getUserDetails();

    IProductDetails getProductDetails();

    IGeoLocation getGeoLocation();

    IMiscellaneousDetails getMiscellaneousDetails();

    String getClient();

    IPaymentDetails getPaymentDetails();

}
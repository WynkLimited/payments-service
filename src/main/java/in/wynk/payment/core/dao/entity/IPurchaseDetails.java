package in.wynk.payment.core.dao.entity;

import in.wynk.common.dto.IGeoLocation;

public interface IPurchaseDetails {
    IAppDetails getAppDetails();
    IUserDetails getUserDetails();
    IPaymentDetails getPaymentDetails();
    IProductDetails getProductDetails();
    IGeoLocation getGeoLocation();
    ISessionDetails getSessionDetails();
}

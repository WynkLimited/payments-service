package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;

public interface IPaymentOptionsRequest {
    IAppDetails getAppDetails();
    IUserDetails getUserDetails();
    String getPlanId();
    String getItemId();
    String getCouponId();
    String getCountryCode();
}

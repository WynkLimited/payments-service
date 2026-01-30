package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;

public interface ICombinedUserDetails {
    IAppDetails getAppDetails();
    IUserDetails getUserDetails();
}

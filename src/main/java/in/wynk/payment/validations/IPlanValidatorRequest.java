package in.wynk.payment.validations;

import in.wynk.common.validations.IBaseRequest;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;

public interface IPlanValidatorRequest extends IBaseRequest {

    boolean isTrialOpted();

    IAppDetails getAppDetails();

    IUserDetails getUserDetails();

    IProductDetails getProductDetails();

}
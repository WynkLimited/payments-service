package in.wynk.payment.validations;

import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.common.validations.IBaseRequest;

public interface IClientValidatorRequest extends IBaseRequest {

    String getService();

    ClientDetails getClientDetails();

}
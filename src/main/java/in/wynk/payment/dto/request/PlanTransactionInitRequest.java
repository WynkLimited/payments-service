package in.wynk.payment.dto.request;

import in.wynk.common.dto.IGeoLocation;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PlanTransactionInitRequest extends AbstractTransactionInitRequest {

    private int planId;
    private boolean trialOpted;
    private boolean autoRenewOpted;
    private boolean mandate;

    private IAppDetails appDetails;
    private IUserDetails userDetails;
    private IGeoLocation geoDetails;

}

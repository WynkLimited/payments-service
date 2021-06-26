package in.wynk.payment.dto.request;

import in.wynk.payment.dto.IAppDetails;
import in.wynk.payment.dto.IUserDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PlanTransactionInitRequest extends AbstractTransactionInitRequest {

    private int planId;
    private boolean trialOpted;
    private boolean autoRenewOpted;

    private IAppDetails appDetails;
    private IUserDetails userDetails;

}

package in.wynk.payment.dto.request;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PlanTransactionInitRequest extends AbstractTransactionInitRequest {

    private int planId;
    private boolean trialOpted;
    private boolean autoRenewOpted;

}

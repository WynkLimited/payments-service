package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.addtobill.AddToBillUserSubscriptionStatusTask;
import in.wynk.payment.gateway.atb.impl.ATBUserSubscriptionDetailsGateway;
import in.wynk.scheduler.task.dto.TaskHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ATBUserSubscriptionStatusHandler extends TaskHandler<AddToBillUserSubscriptionStatusTask> {
    private final ATBUserSubscriptionDetailsGateway userSubscriptionDetailsGateway;

    public ATBUserSubscriptionStatusHandler (ObjectMapper mapper, ATBUserSubscriptionDetailsGateway userSubscriptionDetailsGateway) {
        super(mapper);
        this.userSubscriptionDetailsGateway = userSubscriptionDetailsGateway;
    }

    @Override
    public TypeReference<AddToBillUserSubscriptionStatusTask> getTaskType () {
        return new TypeReference<AddToBillUserSubscriptionStatusTask>() {
        };
    }

    /**
     * @return task group id, should be equal to the group id supplied in task entity
     */
    @Override
    public String getName () {
        return PaymentConstants.ADD_TO_BILL_USER_SUBSCRIPTION_STATUS_TASK;
    }

    @Override
    public void execute (AddToBillUserSubscriptionStatusTask entity) {
        userSubscriptionDetailsGateway.getUserSubscriptionDetails(entity.getSi(), entity.getTransactionId());
    }
}

package in.wynk.payment.dto.addtobill;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.scheduler.task.dto.ITaskEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddToBillUserSubscriptionStatusTask implements ITaskEntity {
    @Analysed
    private String transactionId;
    @Analysed
    private String paymentCode;
    @Analysed
    private String si;

    @Override
    public String getTaskId () {
        return transactionId;
    }

    @Override
    public String getGroupId () {
        return PaymentConstants.ADD_TO_BILL_USER_SUBSCRIPTION_STATUS_TASK;
    }
}

package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.scheduler.task.dto.ITaskEntity;
import lombok.Getter;

@Getter
@AnalysedEntity
public class PurchaseRecord implements ITaskEntity {

    @Analysed
    private String uid;
    @Analysed
    private String productId;
    @Analysed
    private String transactionId;

    @Override
    public String getTaskId() {
        return uid + BaseConstants.DELIMITER + productId;
    }

    @Override
    public String getGroupId() {
        return PaymentConstants.USER_CHURN_GROUP;
    }

}

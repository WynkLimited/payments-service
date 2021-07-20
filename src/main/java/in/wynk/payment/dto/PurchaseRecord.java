package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.scheduler.task.dto.ITaskEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRecord implements ITaskEntity {

    @Analysed
    private String uid;
    @Analysed
    private String transactionId;
    @Analysed
    private IProductDetails productDetails;

    @Override
    public String getTaskId() {
        return uid + BaseConstants.DELIMITER + productDetails.getId();
    }

    @Override
    public String getGroupId() {
        return PaymentConstants.USER_WINBACK;
    }

}

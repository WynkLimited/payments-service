package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.queue.dto.MessageThresholdExceedEvent;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class PaymentReconciliationThresholdExceedEvent extends MessageThresholdExceedEvent {

    @Analysed
    private String uid;
    @Analysed
    private String msisdn;
    @Analysed
    private String itemId;
    @Analysed
    private Integer planId;
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private String transactionId;
    @Analysed
    private PaymentEvent paymentEvent;
    @Analysed
    private PaymentCode paymentCode;
    @Analysed
    private String extTxnId;

}

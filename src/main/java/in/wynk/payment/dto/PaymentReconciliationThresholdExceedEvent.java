package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.stream.dto.MessageThresholdExceedEvent;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PaymentReconciliationThresholdExceedEvent extends MessageThresholdExceedEvent {

    @Analysed
    private String paymentMethodId;
    @Analysed
    private String uid;
    @Analysed
    private String msisdn;
    @Analysed
    private String itemId;
    @Analysed
    private String extTxnId;
    @Analysed
    private String clientAlias;
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private String transactionId;

    @Analysed
    private Integer planId;

    @Analysed
    private PaymentGateway paymentGateway;

    @Analysed
    private PaymentEvent paymentEvent;

}
package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Data;

@Data
@AnalysedEntity
public abstract class AbstractTransactionMessage {
    @Analysed
    private String uid;
    @Analysed
    private String msisdn;
    @Analysed
    private Integer planId;
    @Analysed(name = "txnId")
    private String transactionId;
    @Analysed
    private TransactionEvent transactionEvent;
    @Analysed
    private PaymentCode paymentCode;
}

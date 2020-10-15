package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.commons.constants.BaseConstants;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class PaymentReconciledEvent {

    @Analysed
    private final String uid;
    @Analysed
    private final String msisdn;
    @Analysed
    private final String itemId;
    @Analysed
    private final Integer planId;
    @Analysed
    private final String clientId;
    @Analysed(name = "txnId")
    private final String transactionId;
    private final PaymentCode paymentCode;
    private final TransactionEvent transactionEvent;
    private final TransactionStatus transactionStatus;

    @Analysed(name = BaseConstants.PAYMENT_CODE)
    public String getPaymentCode() {
        return this.paymentCode.getCode();
    }

    @Analysed(name = BaseConstants.TRANSACTION_STATUS)
    public String getTransactionEvent() {
        return this.transactionEvent.getValue();
    }

    @Analysed(name = BaseConstants.TRANSACTION_ID)
    public String getTransactionStatus() {
        return this.transactionStatus.getValue();
    }

}

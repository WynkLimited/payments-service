package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractPaymentRefundResponse {

    @Analysed
    private final double amount;
    @Analysed
    private final int planId;
    @Analysed
    private final String uid;
    @Analysed
    private final String msisdn;
    @Analysed
    private final String itemId;
    @Analysed
    private final String clientAlias;
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private final String transactionId;
    @Analysed(name = BaseConstants.PAYMENT_EVENT)
    private final PaymentEvent paymentEvent;
    @Analysed(name = BaseConstants.TRANSACTION_STATUS)
    private final TransactionStatus transactionStatus;
    @Analysed
    public abstract PaymentCode getPaymentCode();

    public abstract String getExternalReferenceId();

}

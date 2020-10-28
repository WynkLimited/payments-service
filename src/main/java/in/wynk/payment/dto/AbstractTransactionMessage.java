package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public abstract class AbstractTransactionMessage {
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
}

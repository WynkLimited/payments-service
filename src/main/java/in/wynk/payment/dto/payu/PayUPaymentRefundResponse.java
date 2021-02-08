package in.wynk.payment.dto.payu;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.event.PaymentRefundEvent;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PayUPaymentRefundResponse extends AbstractPaymentRefundResponse {
    @Analysed
    private final String authPayUId;
    @Analysed(name = BaseConstants.PAYMENT_CODE)
    private final PaymentCode paymentCode;

    @Override
    public PaymentRefundEvent toRefundEvent() {
        return PaymentRefundEvent.builder()
                .uid(getUid())
                .amount(getAmount())
                .itemId(getItemId())
                .planId(getPlanId())
                .msisdn(getMsisdn())
                .clientAlias(getClientAlias())
                .paymentCode(getPaymentCode())
                .paymentEvent(getPaymentEvent())
                .transactionId(getTransactionId())
                .transactionStatus(getTransactionStatus())
                .build();
    }
}

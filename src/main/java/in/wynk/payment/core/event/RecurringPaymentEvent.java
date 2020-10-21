package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AnalysedEntity
public class RecurringPaymentEvent {

    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private final UUID transactionId;
    @Analysed
    private final PaymentEvent paymentEvent;

}

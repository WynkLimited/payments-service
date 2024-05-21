package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.IAppStoreDetails;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.queue.dto.MessageThresholdExceedEvent;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class ExternalTransactionReportMessageThresholdEvent  extends MessageThresholdExceedEvent {

    @Analysed
    private String transactionId;

    @Analysed
    private String  externalTransactionId;

    @Analysed
    private PaymentEvent paymentEvent;

    @Analysed
    private String initialTransactionId;
}

package in.wynk.payment.core.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import lombok.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@AnalysedEntity
@JsonIgnoreProperties(ignoreUnknown = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentStatusEvent {
    @Analysed
    private final int planId;
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private final String id;
    @Analysed
    private final TransactionStatus transactionStatus;
    @Analysed
    private final PaymentEvent transactionType;
    @Analysed
    private final String redirectUrl;
    @Analysed
    private final String paymentCode;
    @Analysed
    private final String failureReason;
    @Analysed
    private final String clientAlias;
}

package in.wynk.payment.core.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.TransactionStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@AnalysedEntity
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentChargeEvent {
    private String transactionId;
    private String transactionType;
    private TransactionStatus transactionStatus;
    private String to;
    private String from;
    private String orgId;
    private String serviceId;
    private String sessionId;
    private String requestId;
    private String deeplink;
    private String campaignId;
    private String planId;
    private String paymentGatewayCode;
    private boolean trialOpted;
    private boolean mandate;
    private String clientAlias;
}

package in.wynk.payment.dto.aps.request.mandate.cancel;

import in.wynk.payment.dto.aps.common.LOB;
import lombok.Builder;
import lombok.Getter;

import static in.wynk.payment.dto.aps.common.ApsConstant.DEFAULT_CIRCLE_ID;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class CancelMandateRequest {
    private String cancellationRequestId;//new Transaction
    private String mandateTransactionId;
    @Builder.Default
    private String lob = LOB.SI_WYNK.toString();
    private String paymentGateway;
    private String paymentMode;
    @Builder.Default
    private Integer circleId= DEFAULT_CIRCLE_ID;
}

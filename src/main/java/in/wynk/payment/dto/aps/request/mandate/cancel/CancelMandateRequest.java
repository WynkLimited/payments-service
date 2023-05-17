package in.wynk.payment.dto.aps.request.mandate.cancel;

import lombok.Builder;
import lombok.Getter;

import static in.wynk.payment.dto.aps.common.ApsConstant.DEFAULT_CIRCLE_ID;
import static in.wynk.payment.dto.aps.common.ApsConstant.LOB_SI_WYNK;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class CancelMandateRequest {
    private String cancellationRequestId;//new Transaction
    private String mandateTransactionId;
    @Builder.Default
    private String lob = LOB_SI_WYNK;
    private String paymentGateway;
    private String paymentMode;
    @Builder.Default
    private Integer circleId= DEFAULT_CIRCLE_ID;
}

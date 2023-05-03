package in.wynk.payment.dto.aps.request.mandate.cancel;

import in.wynk.payment.dto.request.AbstractCancelMandateRequest;
import lombok.Builder;
import lombok.Getter;
import static in.wynk.payment.dto.aps.common.ApsConstant.LOB_SI_WYNK;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class CancelMandateRequest extends AbstractCancelMandateRequest {
    private String mandateTransactionId;
    private String cancellationRequestId;//new Transaction
    @Builder.Default
    private String lob = LOB_SI_WYNK;
    private String paymentGateway;
    private String paymentMode;
    private String circleId;
}

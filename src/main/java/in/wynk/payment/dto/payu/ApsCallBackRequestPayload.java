package in.wynk.payment.dto.payu;

import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class ApsCallBackRequestPayload extends CallbackRequest implements Serializable {
    private String orderId;
    private String pgId;
    private String redirectionDestination;
    private String start;
    private String end;
    private String interval;
    private String lob;
    private String paymentStatus;

   public String getTransactionId() {
        return this.getOrderId();
    }
}

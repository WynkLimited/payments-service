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
    private String type;
    private String pgSystemId;
    private String bankRefId;
    private String mid;
    private String currency;
    private String paymentMode;
    private String amount;
    private String status;
    private String pg;
    private String bankCode;
    private String vpa;
    private String orderId;
    private String pgId;
    private String redirectionDestination;
    private String start;
    private String end;
    private String interval;
    private String lob;
    private String paymentStatus;
    private String errorCode;
    private String errorMessage;
    private String signature;
    private long timestamp;

   public String getTransactionId() {
        return this.getOrderId();
    }
}

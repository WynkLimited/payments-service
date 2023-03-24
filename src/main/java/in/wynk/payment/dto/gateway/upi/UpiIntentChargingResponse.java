package in.wynk.payment.dto.gateway.upi;

import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "UPI", mode = "INTENT")
public class UpiIntentChargingResponse extends AbstractSeamlessUpiChargingResponse {
    private String pa;
    private String pn;
    private String tr;
    private String am;
    private String cu;
    private String tn;
    private String mc;
    private String mn;
    private String rev;
    private String mode;
    private String recur;
    private String orgId;
    private String block;
    private String amRule;
    private String purpose;
    private String txnType;
    private String recurType;
    private String recurValue;
    private String validityEnd;
    private String validityStart;


}

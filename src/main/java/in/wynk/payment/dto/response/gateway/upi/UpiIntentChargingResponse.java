package in.wynk.payment.dto.response.gateway.upi;

import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "UPI", mode = "intent")
public class UpiIntentChargingResponse extends AbstractSeamlessUpiChargingResponse{
    private String pa;
    private String pn;
    private String tr;
    private String am;
    private String cu;
    private String tn;
    private String mc;
}

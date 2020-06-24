package in.wynk.payment.dto.response;

import in.wynk.payment.enums.Status;
import lombok.Data;

@Data
public class ApbPaymentCallbackResponse {
    private Status paymentStatus;
    private String extTxnId;
    private String msg;
    private String txnId;
}

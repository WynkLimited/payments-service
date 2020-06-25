package in.wynk.payment.dto.response.Apb;

import in.wynk.commons.enums.Status;
import lombok.Data;

@Data
public class ApbPaymentCallbackResponse {
    private Status paymentStatus;
    private String extTxnId;
    private String msg;
    private String txnId;
}

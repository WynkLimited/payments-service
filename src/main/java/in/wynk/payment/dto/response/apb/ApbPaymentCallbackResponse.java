package in.wynk.payment.dto.response.apb;

import in.wynk.common.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@RequiredArgsConstructor
@Getter
public class ApbPaymentCallbackResponse {
    private Status paymentStatus;
    private String extTxnId;
    private String msg;
    private String txnId;
}

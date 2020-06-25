package in.wynk.payment.dto.request.Apb;

import in.wynk.payment.dto.request.PaymentRenewalRequest;
import lombok.Data;

@Data
public class ApbPaymentRenewalRequest extends PaymentRenewalRequest {
    private String txnId;
}

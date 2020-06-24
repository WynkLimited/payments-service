package in.wynk.payment.dto.request;

import lombok.Data;

@Data
public class ApbPaymentRenewalRequest extends PaymentRenewalRequest{
    private String txnId;
}

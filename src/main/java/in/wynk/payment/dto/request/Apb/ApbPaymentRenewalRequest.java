package in.wynk.payment.dto.request.Apb;

import in.wynk.payment.dto.request.PaymentRenewalRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
public class ApbPaymentRenewalRequest extends PaymentRenewalRequest {
    private String txnId;
}

package in.wynk.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRenewalRequest {
    private String paidPartnerProductId;
    private String subsId;
    private String cardToken;
    private String cardNumber;
    private String uid;
    private String amount;
    private String transactionId;
    private String id;
}

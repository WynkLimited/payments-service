package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRenewalRequest {

    private Transaction previousTransaction;
    private String paidPartnerProductId;
    private String subsId;
    private String cardToken;
    private String cardNumber;
    private String uid;
    private String amount;
    private String transactionId;
    private String id;
}

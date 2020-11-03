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
public class PaymentRenewalChargingRequest {

    private Transaction previousTransaction;
    private String uid;
    private String transactionId;
    private Integer planId;
    private String msisdn;
    // Todo - Fetch all this in respective services
    private String paidPartnerProductId;
    private String subsId;
    private String cardToken;
    private String amount;
    private String cardNumber;
    private String id;

}

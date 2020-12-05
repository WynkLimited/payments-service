package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.MerchantTransaction;
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

    private String uid;
    private String msisdn;
    private String transactionId;
    private Transaction previousTransaction;
    private MerchantTransaction merchantTransaction;
    private Integer planId;
    // Todo - Fetch all this in respective services
    private String externalTransactionId;
    private String paidPartnerProductId;
    private String cardNumber;
    private String cardToken;
    private String amount;
    private String id;

}

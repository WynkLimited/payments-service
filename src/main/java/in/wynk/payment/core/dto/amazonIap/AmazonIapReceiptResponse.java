package in.wynk.payment.core.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AmazonIapReceiptResponse {

    boolean betaProduct;
    Long cancelDate;
    String productId;
    String productType;
    Long purchaseDate;
    int quantity;
    String receiptID;
    Long renewalDate;
    String term;
    String termSku;
    boolean testTransaction;
}

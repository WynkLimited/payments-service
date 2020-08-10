package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
public class AmazonIapReceiptResponse {

    @Analysed
    private boolean betaProduct;
    @Analysed
    private Long cancelDate;
    @Analysed
    private String productId;
    @Analysed
    private String productType;
    @Analysed
    private Long purchaseDate;
    @Analysed
    private int quantity;
    @Analysed
    private String receiptID;
    @Analysed
    private Long renewalDate;
    @Analysed
    private String term;
    @Analysed
    private String termSku;
    @Analysed
    private boolean testTransaction;
}

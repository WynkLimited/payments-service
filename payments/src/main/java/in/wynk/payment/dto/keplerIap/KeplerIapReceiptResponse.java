package in.wynk.payment.dto.keplerIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
public class KeplerIapReceiptResponse {

    @Analysed
    private boolean autoRenewing;
    @Analysed
    private boolean betaProduct;
    @Analysed
    private Long cancelDate;
    @Analysed
    private Integer cancelReason;
    @Analysed
    private Long freeTrialEndDate; //null if subscription is not in free trial
    @Analysed
    private String productId;
    @Analysed
    private String productType;
    @Analysed
    private Long purchaseDate;
    @Analysed
    private int quantity;
    @Analysed
    private String receiptId;
    @Analysed
    private Long renewalDate;
    @Analysed
    private String term;
    @Analysed
    private String termSku;
    @Analysed
    private boolean testTransaction;
}

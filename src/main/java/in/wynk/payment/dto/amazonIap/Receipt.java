package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
public class Receipt {
    @Analysed
    private String receiptId;
    @Analysed
    private String sku;
    @Analysed
    private String purchaseDate;

}

package in.wynk.payment.core.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Receipt {
    private String receiptId;
    private String sku;
    private String purchaseDate;

}

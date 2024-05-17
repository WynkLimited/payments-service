package in.wynk.payment.dto.gpbs.response.receipt;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@AnalysedEntity
public class GooglePlayProductReceiptResponse extends AbstractGooglePlayReceiptVerificationResponse {
    private String purchaseTimeMillis;
    private Integer purchaseState; //Possible values are: 0. Purchased 1. Canceled 2. Pending
    private Integer consumptionState;//0. Yet to be consumed 1. Consumed
    private String purchaseToken;
    private String productId;
    private Integer quantity;
    private String regionCode;
    private Integer refundableQuantity;
}

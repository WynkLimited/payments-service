package in.wynk.payment.dto.aps.response.option;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class QuickCheckoutConfig {
    private boolean offer;
    private boolean offerByLine;
    private Map<String, String> payModeFeatureMap;
    private String tncCheckout;
    private boolean quickCheckout;
    private String viewAllPaymentOptions;
}

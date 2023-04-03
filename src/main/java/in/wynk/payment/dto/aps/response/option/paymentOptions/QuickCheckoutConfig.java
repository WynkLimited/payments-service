package in.wynk.payment.dto.aps.response.option.paymentOptions;

import lombok.*;

import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class QuickCheckoutConfig {
    private boolean offer;
    private boolean offerByLine;
    private Map<String, String> payModeFeatureMap;
    private String tncCheckout;
    private boolean quickCheckout;
    private String viewAllPaymentOptions;
}

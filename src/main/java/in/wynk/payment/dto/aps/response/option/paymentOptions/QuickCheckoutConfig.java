package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuickCheckoutConfig implements Serializable {
    private boolean offer;
    private boolean offerByLine;
    private Map<String, String> payModeFeatureMap;
    private String tncCheckout;
    private boolean quickCheckout;
    private String viewAllPaymentOptions;
}

package in.wynk.payment.dto.aps.response.option.paymentOptions;

import in.wynk.payment.dto.aps.common.TokenizationConfig;
import lombok.*;

import java.io.Serializable;

/**
 * @author Nishesh Pandey
 */

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOptionsConfig implements Serializable {
    private TokenizationConfig cardTokenizationConfig;
    private QuickCheckoutConfig quickCheckoutConfig;
    private ExperimentConfig experimentConfig;
    private DormantAccountConfig dormantAccountConfig;
    private boolean applyPaymentCharges;
}

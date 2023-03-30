package in.wynk.payment.dto.aps.response.option;

import in.wynk.payment.dto.aps.common.TokenizationConfig;
import lombok.*;

/**
 * @author Nishesh Pandey
 */

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOptionsConfig {
    private TokenizationConfig cardTokenizationConfig;
    private QuickCheckoutConfig quickCheckoutConfig;
    private ExperimentConfig experimentConfig;
    private DormantAccountConfig dormantAccountConfig;
    private boolean applyPaymentCharges;
}

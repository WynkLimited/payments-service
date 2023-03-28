package in.wynk.payment.dto.aps.response.option;

import in.wynk.payment.dto.aps.common.TokenizationConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOptionsConfig {
    private TokenizationConfig cardTokenizationConfig;
    private QuickCheckoutConfig quickCheckoutConfig;
    private ExperimentConfig experimentConfig;
    private DormantAccountConfig dormantAccountConfig;
    private boolean applyPaymentCharges;
}

package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.constant.WalletConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WalletPaymentsOptions extends AbstractPaymentOptions {
    private List<WalletSubOption> walletSubOption;

    @Override
    public List<WalletSubOption> getOption() {
        return getWalletSubOption();
    }

    @Getter
    @ToString
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletSubOption implements ISubOption {
        private String type;
        private String subType;
        private String health;
        private boolean recommended;
        private String iconUrl;
        private BigDecimal minAmount;
        private boolean insufficientFlowDisabled;
        private String insufficientFlowDisabledLabel;

        @Override
        public String getId() {
            return PaymentConstants.APS.concat("_").concat(WalletConstants.WALLET).concat("_").concat(getSubType());
        }

    }
}

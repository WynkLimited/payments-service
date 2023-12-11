package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.constant.WalletConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import static in.wynk.payment.dto.aps.common.ApsConstant.APS;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletPaymentsOptions extends AbstractPaymentOptions implements Serializable {
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WalletSubOption implements ISubOption, Serializable {
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
            return APS.concat("_").concat(WalletConstants.WALLET).concat("_").concat(getSubType());
        }

    }
}

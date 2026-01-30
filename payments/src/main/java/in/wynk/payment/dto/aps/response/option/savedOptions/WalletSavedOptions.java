package in.wynk.payment.dto.aps.response.option.savedOptions;

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
public class WalletSavedOptions extends AbstractSavedPayOptions implements Serializable {
    private String walletType;
    private String walletId;
    private BigDecimal walletBalance;
    private boolean recommended;
    private boolean isLinked;
    private boolean valid;

    @Override
    public String getId() {
        return APS.concat("_").concat(WalletConstants.WALLET).concat("_").concat(getWalletType());
    }
}

package in.wynk.payment.dto.aps.response.option.savedOptions;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.constant.WalletConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WalletSavedOptions extends AbstractSavedPayOptions implements Serializable {
    private String walletType;
    private String walletId;
    private BigDecimal walletBalance;
    private boolean recommended;
    private boolean isLinked;
    private boolean valid;

    @Override
    public String getId() {
        return PaymentConstants.APS.concat("_").concat(WalletConstants.WALLET).concat("_").concat(getWalletType());
    }
}

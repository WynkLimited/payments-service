package in.wynk.payment.dto.request.charge.wallet;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.common.WalletDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import static in.wynk.payment.constant.WalletConstants.WALLET;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WalletPaymentDetails extends AbstractPaymentDetails {

    @Analysed
    private WalletDetails walletDetails;

    @Override
    public String getPaymentMode() {
        return WALLET;
    }

}

package in.wynk.payment.dto.common;

import in.wynk.payment.constant.WalletConstants;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class WalletOptionInfo extends AbstractPaymentOptionInfo {
    @Override
    public String getType() {
        return WalletConstants.WALLET;
    }
}

package in.wynk.payment.dto.common;

import in.wynk.payment.core.constant.WalletConstants;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class WalletOptionInfo extends AbstractPaymentOptionInfo {
    @Override
    String getType() {
        return WalletConstants.WALLET;
    }
}

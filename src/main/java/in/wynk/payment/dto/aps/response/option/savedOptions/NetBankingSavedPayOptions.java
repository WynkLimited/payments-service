package in.wynk.payment.dto.aps.response.option.savedOptions;

import in.wynk.payment.constant.NetBankingConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class NetBankingSavedPayOptions extends AbstractSavedPayOptions {
    @Override
    public String getId() {
        return PaymentConstants.APS.concat("_").concat(NetBankingConstants.NET_BANKING);
    }
}

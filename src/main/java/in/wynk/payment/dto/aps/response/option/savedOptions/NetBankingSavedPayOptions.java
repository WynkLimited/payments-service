package in.wynk.payment.dto.aps.response.option.savedOptions;

import in.wynk.payment.constant.NetBankingConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class NetBankingSavedPayOptions extends AbstractSavedPayOptions implements Serializable {
    @Override
    public String getId() {
        return PaymentConstants.APS.concat("_").concat(NetBankingConstants.NET_BANKING);
    }
}

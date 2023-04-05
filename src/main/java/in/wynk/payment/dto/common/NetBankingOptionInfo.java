package in.wynk.payment.dto.common;

import in.wynk.payment.constant.NetBankingConstants;
import in.wynk.payment.constant.UpiConstants;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class NetBankingOptionInfo extends AbstractPaymentOptionInfo {

    public String getType() {
        return NetBankingConstants.NET_BANKING;
    }
}

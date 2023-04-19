package in.wynk.payment.dto.aps.common;

import static in.wynk.payment.constant.NetBankingConstants.NET_BANKING;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class NetBankingPaymentInfo extends AbstractPaymentInfo {

    private String bankCode;

    public String getPaymentMode() {
        return NET_BANKING;
    }
}

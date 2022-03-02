package in.wynk.payment.dto.aps.common;

import in.wynk.payment.core.constant.PaymentConstants;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class NetBankingPaymentInfo extends AbstractPaymentInfo {

    private String bankCode;

    public String getPaymentMode() {
        return PaymentConstants.NET_BANKING;
    }
}

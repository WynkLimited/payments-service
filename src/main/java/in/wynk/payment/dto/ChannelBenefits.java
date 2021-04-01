package in.wynk.payment.dto;

import in.wynk.payment.core.constant.PaymentConstants;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class ChannelBenefits extends AbstractPartnerBenefits {
    @Override
    public String getType() {
        return PaymentConstants.CHANNEL;
    }
}

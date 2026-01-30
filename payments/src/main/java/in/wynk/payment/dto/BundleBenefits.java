package in.wynk.payment.dto;

import in.wynk.payment.core.constant.PaymentConstants;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
public class BundleBenefits extends AbstractPartnerBenefits {

    private final List<ChannelBenefits> channelsBenefits;

    @Override
    public String getType() {
        return PaymentConstants.BUNDLE;
    }
}

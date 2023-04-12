package in.wynk.payment.dto.common;

import in.wynk.payment.dto.gpbs.GooglePlayConstant;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class GooglePlayBillingOptionInfo extends AbstractPaymentOptionInfo{
    @Override
    public String getType() {
        return GooglePlayConstant.BILLING;
    }
}

package in.wynk.payment.dto.common;

import in.wynk.payment.dto.addtobill.AddToBillConstants;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class BillingOptionInfo extends AbstractPaymentOptionInfo {
    @Override
    public String getType() {
        return AddToBillConstants.ADDTOBILL;
    }
}

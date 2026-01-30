package in.wynk.payment.dto.common;

import in.wynk.payment.constant.UpiConstants;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class UpiOptionInfo extends AbstractPaymentOptionInfo {
    private String packageId;

    public String getType() {
        return UpiConstants.UPI;
    }
}

package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractUpiPaymentInfo extends AbstractPaymentInfo {

    public abstract String getUpiFlow();

    @Override
    public String getPaymentMode() {
        return "UPI";
    }

}

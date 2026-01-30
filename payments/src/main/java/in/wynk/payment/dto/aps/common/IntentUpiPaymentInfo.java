package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class IntentUpiPaymentInfo extends AbstractUpiPaymentInfo {
    private final String upiFlow = "INTENT_S2S";
}

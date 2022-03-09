package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class CollectUpiPaymentInfo extends AbstractUpiPaymentInfo {
    private String vpa;
    private final String upiFlow = "INTENT_CUSTOM";
}

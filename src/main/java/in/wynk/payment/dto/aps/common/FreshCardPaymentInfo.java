package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class FreshCardPaymentInfo extends AbstractCardPaymentInfo {
    private String cardDetails;
}

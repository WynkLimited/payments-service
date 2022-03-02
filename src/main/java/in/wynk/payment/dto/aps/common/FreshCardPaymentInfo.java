package in.wynk.payment.dto.aps.common;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class FreshCardPaymentInfo extends AbstractCardPaymentInfo {
    private String cardDetails;
}

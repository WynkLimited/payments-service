package in.wynk.payment.dto.aps.common;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CardDetails {
    private String cardNumber;
    private String cvv;
    private String expiryMonth;
    private String expiryYear;
    private String nameOnCard;
}

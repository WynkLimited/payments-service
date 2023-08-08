package in.wynk.payment.dto.aps.common;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CardDetails {
    private String cardNumber;
    private String cardRefNumber;//It will be tokenized card in payment options
    private String nameOnCard;
    private String cvv;
    private String expiryYear;
    private String expiryMonth;
    private String cardType;
    private String cardBin;
}

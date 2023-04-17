package in.wynk.payment.dto.aps.response.renewal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class CardSiRecurringPaymentStatus extends SiRecurringPaymentStatus {
    private String bankRefNo;
    private String cardRefNo;
    private String cardNetwork;
    private String cardBin;
    private String lastDigits;
}

package in.wynk.payment.core.dao.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GooglePlayReceiptDetails extends ReceiptDetails {

    private String purchaseToken;
    private String notificationType;
    private String orderId;

    public boolean isTransactionIdPresent() {
        return Long.parseLong(getReceiptTransactionId()) > 0L;
    }

}

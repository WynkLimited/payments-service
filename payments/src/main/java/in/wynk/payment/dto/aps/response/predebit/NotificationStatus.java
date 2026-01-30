package in.wynk.payment.dto.aps.response.predebit;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
public class NotificationStatus {
    private String txnStatus;
}
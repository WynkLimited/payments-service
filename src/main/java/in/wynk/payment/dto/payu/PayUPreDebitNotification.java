package in.wynk.payment.dto.payu;

import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
public class PayUPreDebitNotification extends AbstractPreDebitNotificationResponse {
}

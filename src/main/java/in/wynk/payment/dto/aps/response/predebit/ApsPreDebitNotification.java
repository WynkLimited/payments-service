package in.wynk.payment.dto.aps.response.predebit;

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
public class ApsPreDebitNotification extends AbstractPreDebitNotificationResponse {
    private String errorMessage;
    private NotificationStatus notificationStatus;
}

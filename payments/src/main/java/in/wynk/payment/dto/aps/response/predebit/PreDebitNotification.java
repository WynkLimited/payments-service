package in.wynk.payment.dto.aps.response.predebit;

import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
public class PreDebitNotification extends AbstractPreDebitNotificationResponse {
    private String errorMessage;
    private NotificationStatus notificationStatus;
}

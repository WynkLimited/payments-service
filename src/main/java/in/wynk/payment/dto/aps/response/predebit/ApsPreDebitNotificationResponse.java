package in.wynk.payment.dto.aps.response.predebit;

import in.wynk.payment.dto.aps.response.charge.AbstractApsExternalChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
public class ApsPreDebitNotificationResponse extends AbstractApsExternalChargingResponse {
    private NotificationStatus notificationStatus;
}

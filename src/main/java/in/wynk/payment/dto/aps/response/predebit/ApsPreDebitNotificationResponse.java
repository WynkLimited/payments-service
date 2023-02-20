package in.wynk.payment.dto.aps.response.predebit;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.aps.response.charge.AbstractApsExternalChargingResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
//@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApsPreDebitNotificationResponse extends AbstractApsExternalChargingResponse {
    private NotificationStatus notificationStatus;
}

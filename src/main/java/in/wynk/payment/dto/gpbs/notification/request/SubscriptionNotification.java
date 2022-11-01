package in.wynk.payment.dto.gpbs.notification.request;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@ToString
public class SubscriptionNotification {
    private String version;
    private Integer notificationType;
    private String purchaseToken;
    private String subscriptionId;
}

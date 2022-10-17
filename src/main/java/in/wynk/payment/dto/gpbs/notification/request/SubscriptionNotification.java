package in.wynk.payment.dto.gpbs.notification.request;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class SubscriptionNotification {
    private String version;
    private Integer notificationType;
    private String purchaseToken;
    private String subscriptionId;
}

package in.wynk.payment.dto.gpbs.notification.request;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@SuperBuilder
public class DeveloperNotification {
    private String version;
    private String packageName;
    private long eventTimeMillis;
    private OneTimeProductNotification oneTimeProductNotification;
    private SubscriptionNotification subscriptionNotification;
    private TestNotification testNotification;
}

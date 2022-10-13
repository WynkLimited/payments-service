package in.wynk.payment.dto.gpbs.notification.request;

/**
 * @author Nishesh Pandey
 */
public class DeveloperNotification {
    private String version;
    private String packageName;
    private long eventTimeMillis;
    private OneTimeProductNotification oneTimeProductNotification;
    private SubscriptionNotification subscriptionNotification;
    private TestNotification testNotification;
}

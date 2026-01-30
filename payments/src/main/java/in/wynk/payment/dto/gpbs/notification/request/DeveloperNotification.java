package in.wynk.payment.dto.gpbs.notification.request;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.web.bind.annotation.RequestAttribute;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeveloperNotification {
    private String version;
    private String packageName;
    private long eventTimeMillis;
    private OneTimeProductNotification oneTimeProductNotification;
    private SubscriptionNotification subscriptionNotification;
    private TestNotification testNotification;
}

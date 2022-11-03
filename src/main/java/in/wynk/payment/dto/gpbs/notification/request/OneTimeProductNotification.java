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
public class OneTimeProductNotification {
    private String version;
    private int notificationType;
    private String purchaseToken;
    private String sku;
}

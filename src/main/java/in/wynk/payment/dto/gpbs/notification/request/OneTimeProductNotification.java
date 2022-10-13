package in.wynk.payment.dto.gpbs.notification.request;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class OneTimeProductNotification {
    private String version;
    private int notificationType;
    private String purchaseToken;
    private String sku;
}

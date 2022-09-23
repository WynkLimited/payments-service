package in.wynk.payment.dto.gpbs;

import in.wynk.payment.dto.IAPNotification;

/**
 * @author Nishesh Pandey
 */
public class GooglePlayCallbackRequest implements IAPNotification {
    @Override
    public String getNotificationType () {
        return null;
    }
}

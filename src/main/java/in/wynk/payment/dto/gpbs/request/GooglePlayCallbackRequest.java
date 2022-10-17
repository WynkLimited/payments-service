package in.wynk.payment.dto.gpbs.request;

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

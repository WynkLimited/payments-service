package in.wynk.payment.service;

import in.wynk.payment.dto.PreDebitNotificationMessage;

/**
 * @author Nishesh Pandey
 */
public interface IPreDebitNotificationServiceV2 {
    void sendPreDebitNotification(PreDebitNotificationMessage request);
}

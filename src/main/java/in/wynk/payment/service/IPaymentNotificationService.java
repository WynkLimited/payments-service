package in.wynk.payment.service;

import in.wynk.payment.dto.UserPlanMapping;

public interface IPaymentNotificationService {

    void handleNotification(String txnId, UserPlanMapping mapping);

}

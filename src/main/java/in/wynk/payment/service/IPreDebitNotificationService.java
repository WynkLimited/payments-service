package in.wynk.payment.service;

import in.wynk.payment.dto.request.PreDebitNotificationRequest;

public interface IPreDebitNotificationService {
    void sendPreDebitNotification(PreDebitNotificationRequest request);
}
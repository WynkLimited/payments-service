package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.UserPlanMapping;

public interface IPaymentNotificationService {

    void handleNotification(Transaction transaction, UserPlanMapping mapping);

}

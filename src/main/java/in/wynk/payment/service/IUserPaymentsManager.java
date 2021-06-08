package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Key;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;

public interface IUserPaymentsManager {

    UserPreferredPayment getPaymentDetails(Key key);

    void savePaymentDetails(UserPreferredPayment userPreferredPayment);

    void deletePaymentDetails(UserPreferredPayment userPreferredPayment);

}
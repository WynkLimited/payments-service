package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.UserPreferredPayment;

public interface IUserPreferredPaymentService {
    UserPreferredPayment getUserPreferredPayments(String uid);
}

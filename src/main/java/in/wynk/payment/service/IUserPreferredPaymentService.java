package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.dto.response.AbstractPaymentDetails;

public interface IUserPreferredPaymentService<R extends AbstractPaymentDetails> {
    WynkResponseEntity<R> getUserPreferredPayments(UserPreferredPayment userPreferredPayment, int planId);
}
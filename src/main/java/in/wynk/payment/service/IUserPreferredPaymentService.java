package in.wynk.payment.service;

import in.wynk.payment.dto.response.AbstractPaymentDetails;

public interface IUserPreferredPaymentService {
    AbstractPaymentDetails getUserPreferredPayments(String uid, String planId);
}
package in.wynk.payment.service;

import in.wynk.payment.dto.request.UserPreferredPaymentsRequest;
import in.wynk.payment.dto.response.AbstractPaymentDetails;

public interface IUserPreferredPaymentService {
    AbstractPaymentDetails getUserPreferredPayments(UserPreferredPaymentsRequest userPreferredPaymentsRequest);
}
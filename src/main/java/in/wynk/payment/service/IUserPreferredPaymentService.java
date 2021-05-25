package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.UserPreferredPaymentsRequest;
import in.wynk.payment.dto.response.AbstractPaymentDetails;

public interface IUserPreferredPaymentService {
    WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails> getUserPreferredPayments(UserPreferredPaymentsRequest userPreferredPaymentsRequest);
}
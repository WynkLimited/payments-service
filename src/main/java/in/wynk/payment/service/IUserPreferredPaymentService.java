package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.dao.entity.Key;
import in.wynk.payment.dto.response.AbstractPaymentDetails;

public interface IUserPreferredPaymentService {
    WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails> getUserPreferredPayments(Key key, int planId);
}
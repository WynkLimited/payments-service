package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.payment.service.impl.AbstractPreferredPaymentDetailsRequest;

public interface IUserPreferredPaymentService<R extends AbstractPaymentDetails, T extends AbstractPreferredPaymentDetailsRequest<?>> {
    WynkResponseEntity<R> getUserPreferredPayments(T preferredPaymentDetailsRequest);
}
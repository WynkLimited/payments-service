package in.wynk.payment.dto.request;

import in.wynk.payment.dto.AbstractProductDetails;
import in.wynk.payment.service.impl.AbstractPreferredPaymentDetailsRequest;

public abstract class AbstractPreferredPaymentDetailsControllerRequest<T extends AbstractProductDetails> extends AbstractPreferredPaymentDetailsRequest<T> implements IPreferredPaymentDetailsControllerRequest {
}
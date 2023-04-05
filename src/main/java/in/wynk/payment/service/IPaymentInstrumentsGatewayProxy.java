package in.wynk.payment.service;

import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;

public interface IPaymentInstrumentsGatewayProxy<T extends PaymentOptionsEligibilityRequest> {
    AbstractPaymentInstrumentsProxy<?,?> load(T request);
}

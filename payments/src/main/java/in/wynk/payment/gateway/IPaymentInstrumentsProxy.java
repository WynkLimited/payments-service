package in.wynk.payment.gateway;

import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;

public interface IPaymentInstrumentsProxy<T extends PaymentOptionsEligibilityRequest> {
    AbstractPaymentInstrumentsProxy<?,?> load(T request);
}

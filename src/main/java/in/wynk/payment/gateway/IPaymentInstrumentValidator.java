package in.wynk.payment.gateway;

import in.wynk.payment.dto.gateway.verify.AbstractPaymentInstrumentValidationResponse;
import in.wynk.payment.dto.request.VerificationRequest;

public interface IPaymentInstrumentValidator<R extends AbstractPaymentInstrumentValidationResponse, T extends VerificationRequest> {
    R verify(T request);
}

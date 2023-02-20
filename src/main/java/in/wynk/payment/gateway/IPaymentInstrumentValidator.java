package in.wynk.payment.gateway;

import in.wynk.payment.dto.gateway.verify.AbstractPaymentInstrumentVerificationResponse;
import in.wynk.payment.dto.request.VerificationRequest;

public interface IPaymentInstrumentValidator<R extends AbstractPaymentInstrumentVerificationResponse, T extends VerificationRequest> {
    R verify(T request);
}

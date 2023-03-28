package in.wynk.payment.gateway;

import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.request.VerificationRequest;

public interface IPaymentInstrumentValidator<R extends AbstractVerificationResponse, T extends VerificationRequest> {
    R verify(T request);
}

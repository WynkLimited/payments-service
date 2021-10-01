package in.wynk.payment.eligibility.service;

import in.wynk.payment.dto.response.PaymentOptionsComputationResponse;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;

public interface IPaymentOptionComputationManager<R extends PaymentOptionsComputationResponse, T extends PaymentOptionsEligibilityRequest> {
    R compute(T request);
}

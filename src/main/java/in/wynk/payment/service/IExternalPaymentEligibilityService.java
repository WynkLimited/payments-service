package in.wynk.payment.service;

import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;

public interface IExternalPaymentEligibilityService {
     Boolean isEligible(PaymentOptionsPlanEligibilityRequest request);
}

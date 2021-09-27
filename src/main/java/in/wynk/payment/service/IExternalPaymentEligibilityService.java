package in.wynk.payment.service;

import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;

public interface IExternalPaymentEligibilityService {
     boolean isEligible(PaymentOptionsPlanEligibilityRequest request);
}

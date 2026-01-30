package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;

public interface IExternalPaymentEligibilityService {
     boolean isEligible(PaymentMethod entity, PaymentOptionsPlanEligibilityRequest request);
     boolean isEligible(PaymentMethod entity, PaymentOptionsItemEligibilityRequest request);
}

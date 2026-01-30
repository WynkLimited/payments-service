package in.wynk.payment.service;

import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;

/**
 * @author Nishesh Pandey
 */
public interface IMerchantIapSubscriptionCancellationService {
    void cancelSubscription (String uid, String transactionId);
}

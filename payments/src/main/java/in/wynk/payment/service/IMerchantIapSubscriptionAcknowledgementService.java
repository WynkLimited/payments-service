package in.wynk.payment.service;

import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;

/**
 * @author Nishesh Pandey
 */
public interface IMerchantIapSubscriptionAcknowledgementService {
    void acknowledgeSubscription (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest);
    void publishAsync (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest);
}

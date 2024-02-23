package in.wynk.payment.service;

import in.wynk.payment.dto.AbstractPaymentAcknowledgementRequest;

/**
 * @author Nishesh Pandey
 */
public interface IMerchantIapSubscriptionAcknowledgementService {

    void acknowledgeSubscription (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest);
    void reportExternalTransactionSubscription ();
    void publishAsync (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest);
}

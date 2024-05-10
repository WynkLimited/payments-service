package in.wynk.payment.extractor;

import in.wynk.pubsub.extractor.AbstractPubSubMessageExtractor;

public class PaymentRenewalChargingPubSubMessageExtractor extends AbstractPubSubMessageExtractor {
    public PaymentRenewalChargingPubSubMessageExtractor(String projectName, String subscriptionName, String bufferInterval) {
        super(projectName, subscriptionName, bufferInterval);
    }
}

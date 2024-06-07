package in.wynk.payment.extractor;

import in.wynk.pubsub.extractor.AbstractPubSubMessageExtractor;

public class PaymentRecurringUnSchedulingPubSubMessageExtractor extends AbstractPubSubMessageExtractor {

    public PaymentRecurringUnSchedulingPubSubMessageExtractor(String projectName, String subscriptionName, String bufferInterval) {
        super(projectName, subscriptionName, bufferInterval);
    }

}

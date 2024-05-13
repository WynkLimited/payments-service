package in.wynk.payment.extractor;

import in.wynk.pubsub.extractor.AbstractPubSubMessageExtractor;

public class PaymentReconciliationGCPMessageExtractor extends AbstractPubSubMessageExtractor {
    public PaymentReconciliationGCPMessageExtractor(String projectName, String subscriptionName, String bufferInterval) {
        super(projectName, subscriptionName, bufferInterval);
    }

}

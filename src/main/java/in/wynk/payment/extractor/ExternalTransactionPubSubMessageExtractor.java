package in.wynk.payment.extractor;

import in.wynk.pubsub.extractor.AbstractPubSubMessageExtractor;

public class ExternalTransactionPubSubMessageExtractor extends AbstractPubSubMessageExtractor {
    public ExternalTransactionPubSubMessageExtractor(String projectName, String subscriptionName, String bufferInterval) {
        super(projectName, subscriptionName, bufferInterval);
    }
}

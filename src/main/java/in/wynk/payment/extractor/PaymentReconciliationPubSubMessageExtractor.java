package in.wynk.payment.extractor;

import in.wynk.pubsub.extractor.AbstractPubSubMessageExtractor;
import org.springframework.beans.factory.annotation.Value;

public class PaymentReconciliationPubSubMessageExtractor extends AbstractPubSubMessageExtractor {

    public PaymentReconciliationPubSubMessageExtractor(@Value("prj-wynk-stg-wcf-svc-01") String projectId, @Value("wcf-starter-poc-sub")String subscriptionName) {
        super(projectId, subscriptionName);
    }
}

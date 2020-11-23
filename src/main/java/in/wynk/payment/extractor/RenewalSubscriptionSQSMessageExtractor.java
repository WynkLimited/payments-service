package in.wynk.payment.extractor;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import in.wynk.queue.constant.BeanConstant;
import in.wynk.queue.extractor.AbstractSQSMessageExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public class RenewalSubscriptionSQSMessageExtractor extends AbstractSQSMessageExtractor {

    @Value("${payment.pooling.queue.recurring.sqs.messages.extractor.batchSize}")
    private int batchSize;

    @Value("${payment.pooling.queue.recurring.sqs.messages.extractor.waitTimeInSeconds}")
    private int waitTimeInSeconds;

    private final String queueName;

    public RenewalSubscriptionSQSMessageExtractor(@Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqs, String queueName) {
        super(sqs);
        this.queueName = queueName;
    }

    @Override
    public ReceiveMessageRequest buildReceiveMessageRequest() {
        return new ReceiveMessageRequest()
                .withMaxNumberOfMessages(batchSize)
                .withQueueUrl(getSqs().getQueueUrl(queueName).getQueueUrl())
                .withWaitTimeSeconds(waitTimeInSeconds);
    }
}

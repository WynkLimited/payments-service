package in.wynk.payment.extractor;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import in.wynk.queue.constant.BeanConstant;
import in.wynk.queue.extractor.AbstractSQSMessageExtractor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

public class UnSchedulePaymentRecurrenceSQSMessageExtractor extends AbstractSQSMessageExtractor {

    @Value("${payment.pooling.queue.recurrence.sqs.messages.extractor.batchSize}")
    private int batchSize;

    @Value("${payment.pooling.queue.recurrence.sqs.messages.extractor.waitTimeInSeconds}")
    private int waitTimeInSeconds;

    private final String queueName;

    public UnSchedulePaymentRecurrenceSQSMessageExtractor(String queueName,
                                                    @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqs) {
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

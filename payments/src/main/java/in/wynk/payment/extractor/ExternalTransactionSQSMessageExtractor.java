package in.wynk.payment.extractor;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import in.wynk.queue.constant.BeanConstant;
import in.wynk.queue.extractor.AbstractSQSMessageExtractor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import static in.wynk.queue.constant.BeanConstant.ALL;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ExternalTransactionSQSMessageExtractor extends AbstractSQSMessageExtractor {
    @Getter
    @Value("${payment.pooling.queue.externalTransaction.report.sqs.messages.extractor.batchSize}")
    private int batchSize;

    @Value("${payment.pooling.queue.externalTransaction.report.sqs.messages.extractor.waitTimeInSeconds}")
    private int waitTimeInSeconds;

    private final String queueName;

    public ExternalTransactionSQSMessageExtractor (String queueName,
                                                           @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqs) {
        super(sqs);
        this.queueName = queueName;
    }


    @Override
    public ReceiveMessageRequest buildReceiveMessageRequest () {
        return new ReceiveMessageRequest()
                .withMessageAttributeNames(ALL)
                .withMaxNumberOfMessages(batchSize)
                .withWaitTimeSeconds(waitTimeInSeconds)
                .withQueueUrl(getSqs().getQueueUrl(queueName).getQueueUrl());
    }
}

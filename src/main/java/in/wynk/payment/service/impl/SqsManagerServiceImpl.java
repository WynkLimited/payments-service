package in.wynk.payment.service.impl;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.config.properties.PaymentProperties;
import in.wynk.payment.core.dto.WynkQueue;
import in.wynk.payment.service.ISqsManagerService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.SQSMessagePublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SqsManagerServiceImpl implements ISqsManagerService {
    @Autowired
    private SQSMessagePublisher sqsMessagePublisher;

    @Override
        public <T> void publishSQSMessage(T message) {
            try {
                WynkQueue queueData = message.getClass().getAnnotation(WynkQueue.class);
                sqsMessagePublisher.publish(SendSQSMessageRequest.<T>builder()
                        .queueName(PaymentProperties.getPropertyValue(queueData.queueName(), String.class))
                        .delaySeconds(PaymentProperties.getPropertyValue(queueData.delaySeconds(),Integer.class))
                        .message(message)
                        .build());
            } catch (Exception e) {
                throw new WynkRuntimeException(QueueErrorType.SQS001, e);
            }
    }

}

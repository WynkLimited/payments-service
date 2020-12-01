package in.wynk.payment.dto;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.queue.dto.FIFOQueueMessageMarker;
import in.wynk.queue.dto.QueueType;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.charging.name}", delaySeconds = "${payment.pooling.queue.charging.sqs.producer.delayInSecond}", queueType = QueueType.FIFO)
public class PaymentRenewalChargingMessage implements FIFOQueueMessageMarker {

    private String id;
    private String uid;
    private String msisdn;
    private String subsId;
    private String amount;
    private String cardToken;
    private String cardNumber;
    private String transactionId;
    private String paidPartnerProductId;
    private Integer planId;
    private PaymentCode paymentCode;
    private Transaction previousTransaction;

    @Override
    public String getMessageGroupId() {
        return paymentCode.getCode();
    }

    @Override
    public String getMessageDeDuplicationId() {
        return transactionId;
    }

}

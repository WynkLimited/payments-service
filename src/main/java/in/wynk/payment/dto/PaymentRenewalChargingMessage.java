package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.queue.dto.FIFOQueueMessageMarker;
import in.wynk.stream.advice.WynkKafkaMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@WynkKafkaMessage(topic = "${wynk.kafka.consumers.listenerFactory.paymentRenewalCharging[0].factoryDetails.topic}")
public class PaymentRenewalChargingMessage {

    @Analysed
    private int attemptSequence;

    @Analysed(name = "old_transaction_id")
    private String id;
    @Analysed
    private String uid;
    @Analysed
    private String msisdn;
    @Analysed
    private String clientAlias;
    @Analysed
    private String paymentCode;

    @Analysed
    private Integer planId;

    /*public String getMessageGroupId() {
        return PaymentCodeCachingService.getFromPaymentCode(this.paymentCode).getCode();
    }

    public String getMessageDeDuplicationId() {
        return id;
    }*/

}
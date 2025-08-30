package in.wynk.payment.core.event;

import in.wynk.stream.advice.KafkaEvent;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "processTransactionForTDR")
public class TdrKafkaMessage {
    private String uid;
    private Integer planId;
    private String referenceTransactionId;
    private String transactionId;
    private double tdr;
    private String tdrStatus;
}
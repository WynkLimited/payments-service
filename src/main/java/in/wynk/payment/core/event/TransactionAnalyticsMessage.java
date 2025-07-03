package in.wynk.payment.core.event;

import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor
@KafkaEvent(topic = "${wynk.kafka.producers.dp.transactions.snapshot.topic}")
public class TransactionAnalyticsMessage {
    private String uid;
    private String msisdn;
    private Integer planId;
    private String itemId;
    private double amountPaid;
    private String client;
    private String couponCode;
    private String accessCountryCode;
    private String stateCode;
    private String ip;
    private double mandateAmount;
    private int renewalAttemptSequence;
    private String referenceTransactionId;
    private String service;
    private String couponGroup;
    private String discountType;
    private double discountValue;
    private String transactionId;
    private long initTimestamp;
    private long exitTimestamp;
    private String paymentEvent;
    private String paymentCode;
    private String transactionStatus;
    private String paymentMethod;
    private String payuId;
    private IAppDetails appDetails;
}

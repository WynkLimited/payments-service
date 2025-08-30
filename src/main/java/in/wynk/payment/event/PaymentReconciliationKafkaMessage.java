package in.wynk.payment.event;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static in.wynk.logging.BaseLoggingMarkers.KAFKA_MESSAGE_CREATOR_ERROR;


@Getter
@Setter
@Builder
@Slf4j
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "paymentReconciliation")
public class PaymentReconciliationKafkaMessage {

    private String uid;
    private String msisdn;
    private String itemId;
    private String paymentCode;
    private String transactionId;
    private PaymentEvent paymentEvent;
    private Integer planId;
    private String extTxnId;
    private String clientAlias;
    private String originalTransactionId;
    private String paymentMethodId;
    private String transactionStatus;
    private double amount;

    public static PaymentReconciliationKafkaMessage from(Transaction transaction) {
        try {
            return PaymentReconciliationKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .itemId(transaction.getItemId())
                    .paymentCode(transaction.getPaymentChannel().getCode())
                    .transactionId(transaction.getIdStr())
                    .originalTransactionId(transaction.getOriginalTransactionId())
                    .paymentEvent(transaction.getType())
                    .planId(transaction.getPlanId())
                    .transactionStatus(transaction.getStatus().toString())
                    .amount(transaction.getAmount())
                    .clientAlias(transaction.getClientAlias())
                    .build();

        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PaymentReconciliationKafkaMessage for txnId: {}", transaction.getIdStr(), e);
        }
        return null;
    }

}

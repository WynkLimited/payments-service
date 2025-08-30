package in.wynk.payment.event;


import in.wynk.payment.constant.UpiConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.aps.request.callback.ApsAutoRefundCallbackRequestPayload;
import in.wynk.payment.dto.aps.request.callback.ApsMandateStatusCallbackRequestPayload;
import in.wynk.payment.dto.payu.PayUAutoRefundCallbackRequestPayload;
import in.wynk.payment.dto.payu.PayUCallbackRequestPayload;
import in.wynk.payment.dto.payu.PayuRealtimeMandatePayload;
import in.wynk.stream.advice.KafkaEvent;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;


import static in.wynk.logging.BaseLoggingMarkers.KAFKA_MESSAGE_CREATOR_ERROR;
import static in.wynk.payment.core.constant.PaymentConstants.PAYU;
import static in.wynk.payment.dto.aps.common.ApsConstant.APS;


@Getter
@Setter
@Builder
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "paymentCallback")
public class PaymentCallbackKafkaMessage {

    private String uid;
    private String msisdn;
    private String transactionId;
    private Integer planId;
    private String clientAlias;
    private String transactionStatus;
    private double amount;
    private String bankCode;
    private String paymentEvent;
    private String extTxnId;
    private String paymentMode;
    private String paymentMethod;
    private String paymentCode;
    private String refundId;
    private String refundSystemId;
    private String refundOrderId;
    private boolean autoRefund;
    private Long refundDate;
    private Long mandateStartDate;
    private Long mandateEndDate;
    private String mandateStatus;
    private String action;
    private String notificationType;

    public static PaymentCallbackKafkaMessage from(ApsCallBackRequestPayload request, Transaction transaction) {
        try {
            return PaymentCallbackKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .transactionId(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .clientAlias(transaction.getClientAlias())
                    .transactionStatus(transaction.getStatus().name())
                    .amount(transaction.getAmount())
                    .bankCode(UpiConstants.UPI.equals(request.getPaymentMode().toString()) ? request.getUpiFlow() : request.getBankCode())
                    .paymentMethod(APS)
                    .paymentCode(APS)
                    .paymentMode(request.getPaymentMode().name())
                    .extTxnId(request.getPgId())
                    .paymentEvent(transaction.getType().name())
                    .build();
        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PaymentCallbackKafkaMessage for APS Generic request: {}", request, e);
        }
        return null;
    }

    public static PaymentCallbackKafkaMessage from(ApsAutoRefundCallbackRequestPayload request, Transaction transaction) {
        try {
            return PaymentCallbackKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .transactionId(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .clientAlias(transaction.getClientAlias())
                    .transactionStatus(transaction.getStatus().name())
                    .amount(transaction.getAmount())
                    .bankCode(UpiConstants.UPI.equals(request.getPaymentMode().toString()) ? request.getUpiFlow() : request.getBankCode())
                    .paymentMethod(APS)
                    .paymentCode(APS)
                    .paymentMode(request.getPaymentMode().name())
                    .extTxnId(request.getPgId())
                    .paymentEvent(transaction.getType().name())
                    .refundDate(request.getRefundDate())
                    .refundOrderId(request.getRefundOrderId())
                    .autoRefund(request.isAutoRefund())
                    .mandateStatus(request.getMandateStatus().name())
                    .build();
        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PaymentCallbackKafkaMessage for APS AutoRefund request: {}", request, e);
        }
        return null;
    }

    public static PaymentCallbackKafkaMessage from(ApsMandateStatusCallbackRequestPayload request, Transaction transaction) {
        try {
            return PaymentCallbackKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .transactionId(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .clientAlias(transaction.getClientAlias())
                    .transactionStatus(transaction.getStatus().name())
                    .amount(transaction.getAmount())
                    .bankCode(UpiConstants.UPI.equals(request.getPaymentMode().toString()) ? request.getUpiFlow() : request.getBankCode())
                    .paymentMethod(APS)
                    .paymentCode(APS)
                    .paymentMode(request.getPaymentMode().name())
                    .extTxnId(request.getPgId())
                    .paymentEvent(transaction.getType().name())
                    .mandateStartDate(request.getMandateStartDate())
                    .mandateEndDate(request.getMandateEndDate())
                    .build();
        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PaymentCallbackKafkaMessage for APS Mandate Check request: {}", request, e);
        }
        return null;
    }

    public static PaymentCallbackKafkaMessage from(PayUCallbackRequestPayload request, Transaction transaction) {
        try {
            return PaymentCallbackKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .transactionId(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .clientAlias(transaction.getClientAlias())
                    .transactionStatus(transaction.getStatus().name())
                    .amount(transaction.getAmount())
                    .bankCode(request.getBankCode())
                    .paymentMethod(PAYU)
                    .paymentCode(PAYU)
                    .paymentMode(request.getMode())
                    .extTxnId(request.getExternalTransactionId())
                    .paymentEvent(transaction.getType().name())
                    .build();
        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PaymentCallbackKafkaMessage for PayU Generic request: {}", request, e);
        }
        return null;
    }

    public static PaymentCallbackKafkaMessage from(PayUAutoRefundCallbackRequestPayload request, Transaction transaction) {
        try {
            return PaymentCallbackKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .transactionId(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .clientAlias(transaction.getClientAlias())
                    .transactionStatus(transaction.getStatus().name())
                    .amount(transaction.getAmount())
                    .paymentEvent(transaction.getType().name())
                    .bankCode(request.getBankCode())
                    .paymentMethod(PAYU)
                    .paymentCode(PAYU)
                    .action(request.getAction())
                    .build();
        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PaymentCallbackKafkaMessage for PayU AutoRefund request: {}", request, e);
        }
        return null;
    }

    public static PaymentCallbackKafkaMessage from(PayuRealtimeMandatePayload request, Transaction transaction) {
        try {
            return PaymentCallbackKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .transactionId(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .clientAlias(transaction.getClientAlias())
                    .transactionStatus(transaction.getStatus().name())
                    .amount(transaction.getAmount())
                    .paymentEvent(transaction.getType().name())
                    .bankCode(request.getBankCode())
                    .paymentMethod("PayU")
                    .paymentCode("PayU")
                    .action(request.getAction())
                    .build();
        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PaymentCallbackKafkaMessage for PayU mandate request: {}", request, e);
        }
        return null;
    }

    public static PaymentCallbackKafkaMessage from(Transaction transaction, String paymentCode, String notificationType) {
        try {
            return PaymentCallbackKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .transactionId(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .clientAlias(transaction.getClientAlias())
                    .transactionStatus(transaction.getStatus().name())
                    .amount(transaction.getAmount())
                    .paymentEvent(transaction.getType().name())
                    .paymentCode(paymentCode)
                    .notificationType(notificationType)
                    .build();
        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PaymentCallbackKafkaMessage for tid: {}", transaction.getIdStr(), e);
        }
        return null;
    }
}

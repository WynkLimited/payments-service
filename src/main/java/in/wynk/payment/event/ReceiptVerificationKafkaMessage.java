package in.wynk.payment.event;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.IapVerificationRequestV2;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.stream.advice.KafkaEvent;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import static in.wynk.logging.BaseLoggingMarkers.KAFKA_MESSAGE_CREATOR_ERROR;

@Getter
@Setter
@Builder
@Slf4j
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "receiptVerification")
public class ReceiptVerificationKafkaMessage {
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
    private String sid;
    private String action;
    private String deviceId;
    private String stateCode;
    private String os;
    private String service;
    private String appId;
    private String skuId;

    public static ReceiptVerificationKafkaMessage from(IapVerificationRequest request, Transaction transaction) {
        try {
            return ReceiptVerificationKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .transactionId(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .clientAlias(transaction.getClientAlias())
                    .transactionStatus(transaction.getStatus().name())
                    .amount(transaction.getAmount())
                    .paymentEvent(transaction.getType().name())
                    .paymentCode(request.getPaymentCode())
                    .paymentMethod(request.getPaymentCode())
                    .deviceId(request.getDeviceId())
                    .sid(request.getSid())
                    .stateCode(request.getGeoLocation() != null ? request.getGeoLocation().getStateCode() : null)
                    .os(request.getOs())
                    .service(request.getService())
                    .appId(request.getAppId())
                    .build();
        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating ReceiptVerificationKafkaMessage for request: {}", request, e);
        }
        return null;
    }

    public static ReceiptVerificationKafkaMessage from(IapVerificationRequestV2 request, Transaction transaction) {
        try {
            return ReceiptVerificationKafkaMessage.builder()
                    .uid(transaction.getUid())
                    .msisdn(transaction.getMsisdn())
                    .transactionId(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .clientAlias(transaction.getClientAlias())
                    .transactionStatus(transaction.getStatus().name())
                    .amount(transaction.getAmount())
                    .paymentEvent(transaction.getType().name())
                    .paymentCode(request.getPaymentCode() != null ? request.getPaymentCode().getCode() : null)
                    .paymentMethod(request.getPaymentCode() != null ? request.getPaymentCode().getCode() : null)
                    .deviceId(request.getAppDetails() != null ? request.getAppDetails().getDeviceId() : null)
                    .sid(request.getSessionDetails() != null ? request.getSessionDetails().getSessionId() : null)
                    .stateCode(request.getGeoLocation() != null ? request.getGeoLocation().getStateCode() : null)
                    .os(request.getAppDetails().getOs())
                    .appId(request.getAppDetails().getAppId())
                    .build();
        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating ReceiptVerificationKafkaMessage for request: {}", request, e);
        }
        return null;
    }
}

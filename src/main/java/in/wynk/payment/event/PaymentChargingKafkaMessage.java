package in.wynk.payment.event;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.stream.advice.KafkaEvent;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import static in.wynk.logging.BaseLoggingMarkers.KAFKA_MESSAGE_CREATOR_ERROR;

@Getter
@Setter
@Builder
@Slf4j
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "paymentCharging")
public class PaymentChargingKafkaMessage {
    private String uid;
    private String msisdn;
    private String transactionId;
    private Integer planId;
    private String clientAlias;
    private String transactionStatus;
    private double amount;
    private String paymentEvent;
    private String extTxnId;
    private String paymentMode;
    private String paymentMethod;
    private String paymentCode;
    private String os;
    private String service;
    private String bankName;
    private String appId;
    private String productType;
    private String stateCode;
    private String paymentId;

    public static PaymentChargingKafkaMessage from(AbstractPaymentChargingRequest request, Transaction transaction) {
        try {
            return PaymentChargingKafkaMessage.builder()
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
                    .paymentMode(request.getPaymentDetails() != null ? request.getPaymentDetails().getPaymentMode() : null)
                    .os(request.getOs())
                    .service(request.getService())
                    .bankName(request.getPaymentDetails() != null ? request.getPaymentDetails().getMerchantName() : null)
                    .appId(request.getAppId())
                    .productType(request.getProductDetails() != null ? request.getProductDetails().getType() : null)
                    .stateCode(request.getGeoLocation() != null ? request.getGeoLocation().getStateCode() : null)
                    .paymentId(request.getPaymentId())
                    .build();

        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PaymentChargingKafkaMessage for request: {}", request, e);
        }
        return null;
    }
}

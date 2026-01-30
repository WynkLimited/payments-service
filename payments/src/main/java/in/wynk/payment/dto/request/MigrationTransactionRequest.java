package in.wynk.payment.dto.request;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.common.messages.PaymentRecurringSchedulingMessage;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import lombok.Builder;
import lombok.Getter;

import java.util.Date;
import java.util.Map;

@Getter
@Builder
public class MigrationTransactionRequest {

    private int planId;
    private String uid;
    private String msisdn;
    private String clientAlias;
    private String paymentCode;
    private PaymentEvent event;
    private Date nextChargingDate;
    private Map<String, String> paymentMetaData;

    public static MigrationTransactionRequest from(PaymentRecurringSchedulingMessage message) {
        return MigrationTransactionRequest.builder().planId(message.getPlanId()).uid(message.getUid()).msisdn(message.getMsisdn()).clientAlias(message.getClientAlias()).paymentCode(message.getPaymentCode()).event(message.getEvent()).paymentMetaData(message.getPaymentMetaData()).build();
    }

    public PaymentGateway getPaymentCode() {
        return PaymentCodeCachingService.getFromPaymentCode(paymentCode);
    }

}
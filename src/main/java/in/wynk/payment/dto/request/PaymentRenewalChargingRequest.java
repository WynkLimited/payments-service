package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.PaymentGateway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRenewalChargingRequest {
    private String id;
    private String uid;
    private String msisdn;
    private int attemptSequence;
    private Integer planId;
    private String clientAlias;
    private String paymentCode;
    private PaymentGateway paymentGateway;
}
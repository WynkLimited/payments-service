package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanRenewalRequest {
    private int planId;
    private String uid;
    private String msisdn;
    private String clientAlias;
    private PaymentCode paymentCode;
}

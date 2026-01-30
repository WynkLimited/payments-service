package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanRenewalRequest {

    private int planId;
    @Analysed(name = "old_transaction_id")
    private String txnId;
    private String uid;
    private String msisdn;
    private String clientAlias;
    private PaymentGateway paymentGateway;

}
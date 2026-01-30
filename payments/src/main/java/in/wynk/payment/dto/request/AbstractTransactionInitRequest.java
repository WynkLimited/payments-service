package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class AbstractTransactionInitRequest {

    @Setter
    private double amount;
    @Setter
    @Builder.Default
    private double mandateAmount = -1;
    @Setter
    private double discount;
    @Analysed(name = "old_transaction_id")
    private String txnId;

    private String uid;
    private String msisdn;
    private String couponId;
    private String clientAlias;
    @Builder.Default
    private String status = TransactionStatus.INPROGRESS.getValue();

    @Setter
    private PaymentEvent event;
    private PaymentGateway paymentGateway;

}
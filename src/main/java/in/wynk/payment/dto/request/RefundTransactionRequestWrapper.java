package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefundTransactionRequestWrapper {

    private PaymentRefundInitRequest request;
    private Transaction originalTransaction;
    @Analysed(name = "old_transaction_id")
    private String txnId;

}

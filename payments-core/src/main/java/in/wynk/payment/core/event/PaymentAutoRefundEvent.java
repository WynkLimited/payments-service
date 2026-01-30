package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

@Getter
@Builder
@AnalysedEntity
public class PaymentAutoRefundEvent {
    @Analysed
    private Transaction transaction;
    @Analysed
    private String clientAlias;
    @Autowired
    private IPurchaseDetails purchaseDetails;
}

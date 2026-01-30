package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class ClientCallbackEvent {
    @Analysed
    private String uid;
    @Analysed
    private String msisdn;
    @Analysed
    private String itemId;
    @Analysed
    private Integer planId;
    @Analysed
    private String clientAlias;
    @Analysed
    private String transactionId;
    @Analysed
    private String transactionStatus;

    public static ClientCallbackEvent from(Transaction transaction) {
        return ClientCallbackEvent.builder()
                .transactionStatus(transaction.getStatus().getValue())
                .clientAlias(transaction.getClientAlias())
                .transactionId(transaction.getIdStr())
                .msisdn(transaction.getMsisdn())
                .planId(transaction.getPlanId())
                .itemId(transaction.getItemId())
                .uid(transaction.getUid())
                .build();
    }

}

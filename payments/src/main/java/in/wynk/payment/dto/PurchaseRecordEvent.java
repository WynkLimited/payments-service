package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AnalysedEntity
@RequiredArgsConstructor
public class PurchaseRecordEvent {
    @Analysed
    private final String sid;
    @Analysed
    private final String uid;
    @Analysed
    private final String msisdn;
    @Analysed
    private final String clientAlias;
    @Analysed
    private final String transactionId;
    @Analysed
    private final AppDetails appDetails;
    @Analysed
    private final AbstractProductDetails productDetails;
}

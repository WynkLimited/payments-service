package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class LatestReceiptResponse {

    @Analysed
    private final int planId;

    @Analysed
    private final String itemId;

    @Analysed
    private final String extTxnId;

    @Analysed
    private final String couponCode;

    @Analysed
    private final boolean freeTrial;

    @Analysed
    private final boolean autoRenewal;


    @Analysed
    @Setter
    private String successUrl;

    @Analysed
    @Setter
    private String failureUrl;

}
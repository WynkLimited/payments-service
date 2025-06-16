package in.wynk.payment.dto.keplerIap;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class KeplerLatestReceiptResponse extends LatestReceiptResponse {

    @Analysed
    private final String keplerUserId;
    @Analysed
    private final KeplerIapReceiptResponse keplerIapReceiptResponse;
    @Analysed
    private final String service;

}
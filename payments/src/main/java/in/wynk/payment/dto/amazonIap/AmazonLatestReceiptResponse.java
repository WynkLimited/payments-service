package in.wynk.payment.dto.amazonIap;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class AmazonLatestReceiptResponse extends LatestReceiptResponse {

    @Analysed
    private final String amazonUserId;
    @Analysed
    private final AmazonIapReceiptResponse amazonIapReceiptResponse;
    @Analysed
    private final String service;

}

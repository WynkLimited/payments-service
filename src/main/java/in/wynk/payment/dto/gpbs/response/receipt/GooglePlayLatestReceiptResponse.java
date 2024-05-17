package in.wynk.payment.dto.gpbs.response.receipt;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@SuperBuilder
@AnalysedEntity
public class GooglePlayLatestReceiptResponse extends LatestReceiptResponse {

    @Analysed
    private final String purchaseToken;

    @Analysed
    private Integer notificationType;

    @Analysed
    private String subscriptionId;

    @Analysed
    private final String packageName;

    @Analysed
    private final String service;

    @Analysed
    private final String skuId;

    @Analysed
    private final AbstractGooglePlayReceiptVerificationResponse googlePlayResponse;
}

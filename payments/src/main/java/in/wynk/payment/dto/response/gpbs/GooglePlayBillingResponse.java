package in.wynk.payment.dto.response.gpbs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.BaseResponse;
import in.wynk.common.dto.SessionResponse;
import in.wynk.payment.dto.PageResponseDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayPaymentDetails;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GooglePlayBillingResponse  extends BaseResponse<GooglePlayBillingResponse.GooglePlayBillingData, Void> {

    @Analysed
    private  GooglePlayBillingData data;

    @Getter
    @Builder
    @AnalysedEntity
    public static class GooglePlayBillingData {
        @Analysed
        private GooglePlayPaymentDetails paymentDetails;

        @Analysed
        PageResponseDetails pageDetails;
    }
}

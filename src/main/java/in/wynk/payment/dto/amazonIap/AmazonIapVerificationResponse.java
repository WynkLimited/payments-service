package in.wynk.payment.dto.amazonIap;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.commons.dto.BaseResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class AmazonIapVerificationResponse extends BaseResponse<AmazonIapVerificationResponse.AmazonIapVerification> {

    @Analysed
    private final AmazonIapVerification data;

    @Getter
    @Builder
    @AnalysedEntity
    public static class AmazonIapVerification {
        @Analysed
        private final String url;
    }

}

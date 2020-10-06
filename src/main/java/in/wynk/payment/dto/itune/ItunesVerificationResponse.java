package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.commons.dto.BaseResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ItunesVerificationResponse extends BaseResponse<ItunesVerificationResponse.ItunesReceiptVerification> {

    @Analysed
    private final ItunesReceiptVerification data;

    @Getter
    @Builder
    @AnalysedEntity
    public static class ItunesReceiptVerification {
        @Analysed
        private final String url;
    }

}

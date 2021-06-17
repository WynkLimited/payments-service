package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.BaseResponse;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IapVerificationResponse extends BaseResponse<IapVerificationResponse.IapVerification, Void> {

    @Analysed
    private IapVerification data;

    @Getter
    @Builder
    @AnalysedEntity
    public static class IapVerification {
        @Analysed
        private final String url;
    }

}

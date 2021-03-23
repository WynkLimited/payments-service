package in.wynk.payment.dto.phonepe;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.BaseResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AnalysedEntity
@NoArgsConstructor
public class PhonePeChargingResponse extends BaseResponse<PhonePeChargingResponse.PhonePeChargingResponseWrapper> {

    @Analysed
    private PhonePeStatusEnum code;
    private PhonePeChargingResponseWrapper data;

    @Getter
    public static class PhonePeChargingResponseWrapper {
        @Analysed
        private String redirectType;
        @Analysed
        private String redirectURL;
    }

}

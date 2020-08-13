package in.wynk.payment.dto.itune;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.IapVerificationRequest;
import lombok.Getter;

@Getter
@AnalysedEntity
public class ItunesVerificationRequest extends IapVerificationRequest {

    @Analysed
    private String receipt;

}

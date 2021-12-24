package in.wynk.payment.dto.response.payu;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.IVerificationResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@AnalysedEntity
public class PayUVpaVerificationResponse implements IVerificationResponse {

    @Setter
    @Analysed
    private boolean isValid;

    @Analysed
    private int isVPAValid;

    @Analysed
    private String vpa;

    @Analysed
    private String status;

    @Analysed
    private String payerAccountName;

}
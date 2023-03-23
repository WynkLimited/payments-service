package in.wynk.payment.presentation.dto.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VpaVerifyUserPaymentResponse extends VerifyUserPaymentResponse {
    private boolean isAutoPayVPAValid;
    private boolean isAutoPayBankValid;
    private String vpa;
    private String payerAccountName;
}

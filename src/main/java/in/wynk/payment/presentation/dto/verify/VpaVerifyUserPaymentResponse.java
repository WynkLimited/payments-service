package in.wynk.payment.presentation.dto.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VpaVerifyUserPaymentResponse extends VerifyUserPaymentResponse {
    private boolean autoPayVPAValid;
    private boolean autoPayBankValid;
    private String vpa;
    private String payerAccountName;

    @JsonProperty("isAutoPayVPAValid")
    public boolean isAutoPayVPAValid () {
        return autoPayVPAValid;
    }

    @JsonProperty("isAutoPayBankValid")
    public boolean isAutoPayBankValid () {
        return autoPayBankValid;
    }
}

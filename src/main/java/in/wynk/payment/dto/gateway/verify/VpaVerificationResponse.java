package in.wynk.payment.dto.gateway.verify;

import in.wynk.payment.dto.gateway.IVpaValidationSpec;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class VpaVerificationResponse extends AbstractPaymentInstrumentVerificationResponse implements IVpaValidationSpec {
    private String vpa;
    private String payeeAccountName;
    private String errorMessage;
    @Override
    public String getPayerAccountName () {
        return this.payeeAccountName;
    }
}

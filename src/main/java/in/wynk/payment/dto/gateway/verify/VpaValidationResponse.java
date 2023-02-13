package in.wynk.payment.dto.gateway.verify;

import in.wynk.payment.dto.gateway.IVpaValidationSpec;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class VpaValidationResponse extends AbstractPaymentInstrumentValidationResponse implements IVpaValidationSpec {
    private String vpa;
    private String payerAccountName;
}

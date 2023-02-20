package in.wynk.payment.dto.gateway.verify;

import in.wynk.payment.dto.gateway.ICardValidationSpec;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

@Getter
@ToString
@SuperBuilder
public class BinVerificationResponse extends AbstractPaymentInstrumentVerificationResponse implements ICardValidationSpec {

    private String cardType;
    private String cardNetwork; //Visa, master etc
    private boolean isDomestic;
    private String issuingBank;
    private String cardCategory; //Credit or Debit
    private boolean zeroRedirect;
}

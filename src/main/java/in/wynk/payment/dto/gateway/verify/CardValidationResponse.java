package in.wynk.payment.dto.gateway.verify;

import in.wynk.payment.dto.gateway.ICardValidationSpec;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

@Getter
@ToString
@SuperBuilder
public class CardValidationResponse extends AbstractPaymentInstrumentValidationResponse implements ICardValidationSpec {

    private String type;
    private String level;
    private String brand;
    private String issuingBank;

    private boolean domestic;
    private boolean autoRenew;
    private boolean zeroRedirect;

    @Override
    public Optional<String> getLevel() {
        return Optional.ofNullable(level);
    }
}

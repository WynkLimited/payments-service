package in.wynk.payment.dto.gateway.charge.upi;

import in.wynk.payment.dto.gateway.IUpiIntentSpec;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Optional;
import static in.wynk.payment.constant.UpiConstants.UPI_MERCHANT_CODE;

@Getter
@ToString
@SuperBuilder
public class SeamlessUpiChargingGatewayResponse extends AbstractUpiChargingGatewayResponse implements IUpiIntentSpec {

    private String payeeVpa;
    private String currencyCode;
    private String amountToBePaid;
    private String merchantOrderID;
    private String payeeDisplayName;
    private String transactionNote;
    @Builder.Default
    private String merchantCategoryCode = UPI_MERCHANT_CODE;

    public Optional<String> getCurrencyCode() {
        return Optional.ofNullable(currencyCode);
    }

    public Optional<String> getTransactionNote() {
        return Optional.ofNullable(transactionNote);
    }

}

package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class WalletSavedDetails extends AbstractSavedPaymentDTO {
    @JsonProperty("is_linked")
    private boolean linked;
    @JsonProperty("is_valid")
    private boolean valid;
    @JsonProperty("can_checkout")
    private boolean canCheckOut;
    @JsonProperty("add_money_allowed")
    private boolean addMoneyAllowed;
    @JsonProperty("wallet_id")
    private String walletId;
    @JsonProperty("wallet_balance")
    private double balance;
    @JsonProperty("deficit_balance")
    private double minBalance;
}

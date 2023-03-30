package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * @author Nishesh Pandey
 */
@Getter
@NoArgsConstructor
@SuperBuilder
public class WalletSavedDetails extends SavedPaymentDTO {
    @JsonProperty("is_linked")
    private boolean linked;
    @JsonProperty("is_valid")
    private boolean valid;
    @JsonProperty("can_checkout")
    private boolean canCheckOut;
    @JsonProperty("add_money_allowed")
    private boolean addMoneyAllowed;
    @JsonProperty("wallet_id")
    private String id;
    @JsonProperty("wallet_balance")
    private BigDecimal balance;
    @JsonProperty("deficit_balance")
    private BigDecimal minBalance;
}

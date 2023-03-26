package in.wynk.payment.dto.aps.response.option;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpiPaymentOptions.class, name = "UPI"),
        @JsonSubTypes.Type(value = WalletPaymentsOptions.class, name = "WALLETS"),
        @JsonSubTypes.Type(value = NetBankingPaymentOptions.class, name = "NETBANKING")
})
public class PaymentOptions {
    private String type;
    private BigDecimal minAmount;
}

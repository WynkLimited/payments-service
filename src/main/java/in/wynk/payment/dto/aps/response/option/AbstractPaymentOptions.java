package in.wynk.payment.dto.aps.response.option;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", defaultImpl = DefaultPaymentOptions.class, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpiPaymentOptions.class, name = "UPI"),
        @JsonSubTypes.Type(value = CardPaymentOptions.class, name = "CARDS"),
        @JsonSubTypes.Type(value = WalletPaymentsOptions.class, name = "WALLETS"),
        @JsonSubTypes.Type(value = NetBankingPaymentOptions.class, name = "NETBANKING")
})
public abstract class AbstractPaymentOptions {
    private String type;
    private BigDecimal minAmount;
}

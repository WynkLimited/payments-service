package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = PaymentConstants.FIELD_TYPE, defaultImpl = DefaultPaymentOptions.class, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpiPaymentOptions.class, name = UpiConstants.UPI),
        @JsonSubTypes.Type(value = CardPaymentOptions.class, name = CardConstants.CARDS),
        @JsonSubTypes.Type(value = WalletPaymentsOptions.class, name = WalletConstants.WALLETS),
        @JsonSubTypes.Type(value = NetBankingPaymentOptions.class, name = NetBankingConstants.NETBANKING)
})
public abstract class AbstractPaymentOptions {
    private String type;
    private BigDecimal minAmount;

    public abstract <T extends ISubOption> List<T> getOption();

    public interface ISubOption {
         String getId();

         default boolean isEnabled() {
             return Boolean.TRUE;
         }
    }
}

package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import static in.wynk.payment.constant.CardConstants.CARDS;
import static in.wynk.payment.constant.NetBankingConstants.NETBANKING;
import static in.wynk.payment.constant.UpiConstants.UPI;
import static in.wynk.payment.constant.WalletConstants.WALLETS;
import static in.wynk.payment.dto.aps.common.ApsConstant.FIELD_TYPE;

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
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = FIELD_TYPE, defaultImpl = DefaultPaymentOptions.class, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpiPaymentOptions.class, name = UPI),
        @JsonSubTypes.Type(value = CardPaymentOptions.class, name = CARDS),
        @JsonSubTypes.Type(value = WalletPaymentsOptions.class, name = WALLETS),
        @JsonSubTypes.Type(value = NetBankingPaymentOptions.class, name = NETBANKING)
})
public abstract class AbstractPaymentOptions implements Serializable {
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

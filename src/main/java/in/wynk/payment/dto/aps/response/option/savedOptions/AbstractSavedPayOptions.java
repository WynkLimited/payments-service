package in.wynk.payment.dto.aps.response.option.savedOptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.CardConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.UpiConstants;
import in.wynk.payment.core.constant.WalletConstants;
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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = PaymentConstants.FIELD_TYPE, defaultImpl = NetBankingSavedPayOptions.class, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WalletSavedOptions.class, name = WalletConstants.WALLETS),
        @JsonSubTypes.Type(value = UpiSavedOptions.class, name = UpiConstants.UPI),
        @JsonSubTypes.Type(value = CardSavedPayOptions.class, name = CardConstants.CARDS)
})
public abstract class AbstractSavedPayOptions {
    private String type;
    private Integer order;
    private BigDecimal minAmount;
    private String health;
    private Integer healthSr;
    private boolean preferred;
    private boolean showOnQuickCheckout;
    private boolean hidden;
    private String rank;
    private String iconUrl;
    private boolean favourite;
}

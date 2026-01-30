package in.wynk.payment.dto.aps.response.option.savedOptions;

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

import static in.wynk.payment.constant.CardConstants.CARDS;
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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = FIELD_TYPE, defaultImpl = NetBankingSavedPayOptions.class, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WalletSavedOptions.class, name = WALLETS),
        @JsonSubTypes.Type(value = UpiSavedOptions.class, name = UPI),
        @JsonSubTypes.Type(value = CardSavedPayOptions.class, name = CARDS)
})
public abstract class AbstractSavedPayOptions implements Serializable {
    private String type;
    private Integer order;
    private String health;
    private String rank;
    private String iconUrl;

    private Integer healthSr;
    private BigDecimal minAmount;

    private boolean hidden;
    private boolean favourite;
    private boolean preferred;
    private boolean showOnQuickCheckout;

    public abstract String getId();
}

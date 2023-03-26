package in.wynk.payment.dto.aps.response.option;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

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
        @JsonSubTypes.Type(value = WalletSavedOptions.class, name = "WALLETS"),
        @JsonSubTypes.Type(value = UpiSavedOptions.class, name = "UPI")
})
public class SavedPayOptions {
    private String type;
    private String order;
    private String minAmount;
    private String health;
    private String healthSr;
    private boolean preferred;
    private boolean showOnQuickCheckout;
    private boolean hidden;
    private String rank;
    private String iconUrl;
    private boolean valid;
    private boolean favourite;
}

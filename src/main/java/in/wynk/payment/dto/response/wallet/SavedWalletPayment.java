package in.wynk.payment.dto.response.wallet;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.SavedDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class SavedWalletPayment extends SavedDetails {
    private boolean isLinked;
    @JsonProperty("can_checkout")
    private boolean canCheckout;
    private Double balance;
    private boolean locked;
    private Alert alert;

    @Getter
    @AllArgsConstructor
    @Builder
    @AnalysedEntity
    public static class Alert {
        private String message;
        private String level;
    }
}

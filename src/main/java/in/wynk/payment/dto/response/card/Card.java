package in.wynk.payment.dto.response.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentMethodDTO;
import in.wynk.payment.dto.response.UiDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class Card extends AbstractPaymentMethodDTO {

    @JsonProperty("ui_details")
    private CardUiDetails uiDetails;

    @Getter
    @SuperBuilder
    @AnalysedEntity
    public static class CardUiDetails extends UiDetails {
        @JsonProperty("supported_card_icons")
        private Map<String, String> supportedCardIcons;
    }
}

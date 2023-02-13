package in.wynk.payment.dto.response.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.PaymentMethodDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class Card extends PaymentMethodDetails {

    @JsonProperty("saved_details")
    private List<CardSavedDetails> savedDetails;

    @Getter
    @AllArgsConstructor
    @Builder
    @AnalysedEntity
    public static class CardSavedDetails {
        private String card;
    }

}

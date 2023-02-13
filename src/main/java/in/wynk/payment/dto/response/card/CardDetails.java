package in.wynk.payment.dto.response.card;

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
public class CardDetails extends SavedDetails {
    private SavedCardDetails card;

    @Getter
    @AllArgsConstructor
    @Builder
    @AnalysedEntity
    public static class SavedCardDetails {
        private String number;
        private String exp;
    }
}

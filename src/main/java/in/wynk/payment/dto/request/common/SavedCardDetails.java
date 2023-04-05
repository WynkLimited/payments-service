package in.wynk.payment.dto.request.common;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

import static in.wynk.payment.constant.CardConstants.SAVED_CARD_TYPE;

@Getter
@AnalysedEntity
public class SavedCardDetails extends AbstractCardDetails {
    @Analysed
    private String cardToken;
    @Override
    @Analysed(name = "cardType")
    public String getType() {
        return SAVED_CARD_TYPE;
    }

}

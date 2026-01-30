package in.wynk.payment.dto.request.common;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

import static in.wynk.payment.constant.CardConstants.FRESH_CARD_TYPE;

@Getter
public class FreshCardDetails extends AbstractCardDetails {
    private String cardHolderName;
    private String cardNumber;
    private CardExpiryInfo expiryInfo;

    @Override
    public String getType () {
        return FRESH_CARD_TYPE;
    }
}

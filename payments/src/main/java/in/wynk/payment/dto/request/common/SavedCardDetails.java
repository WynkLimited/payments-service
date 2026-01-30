package in.wynk.payment.dto.request.common;

import lombok.Getter;

import static in.wynk.payment.constant.CardConstants.SAVED_CARD_TYPE;

@Getter
public class SavedCardDetails extends AbstractCardDetails {
    private String cardToken;

    @Override
    public String getType() {
        return SAVED_CARD_TYPE;
    }
}

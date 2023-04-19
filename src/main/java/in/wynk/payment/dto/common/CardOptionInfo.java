package in.wynk.payment.dto.common;

import in.wynk.payment.constant.CardConstants;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class CardOptionInfo extends AbstractPaymentOptionInfo {

    public String getType() {
        return CardConstants.CARD;
    }

}

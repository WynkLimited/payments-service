package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.CardConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public class CardPaymentOptions extends AbstractPaymentOptions {

    @Override
    public List<CardSubOption> getOption() {
        return Collections.singletonList(new CardSubOption());
    }

    private static class CardSubOption implements ISubOption {
        @Override
        public String getId() {
            return CardConstants.CARD.concat("002");
        }

    }
}

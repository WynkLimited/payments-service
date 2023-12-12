package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.constant.CardConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import static in.wynk.payment.dto.aps.common.ApsConstant.APS;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardPaymentOptions extends AbstractPaymentOptions implements Serializable {

    @Override
    public List<CardSubOption> getOption() {
        return Collections.singletonList(new CardSubOption());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CardSubOption implements ISubOption, Serializable {
        @Override
        public String getId() {
            return APS.concat("_").concat(CardConstants.CARD);
        }

    }
}

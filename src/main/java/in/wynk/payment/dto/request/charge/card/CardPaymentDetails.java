package in.wynk.payment.dto.request.charge.card;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.payment.dto.request.common.AbstractCardDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import static in.wynk.payment.constant.CardConstants.CARD;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class CardPaymentDetails extends AbstractPaymentDetails {

    @Analysed
    private AbstractCardDetails cardDetails;

    @Override
    public String getPaymentMode() {
        return CARD;
    }
}

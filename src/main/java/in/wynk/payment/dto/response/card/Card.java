package in.wynk.payment.dto.response.card;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentMethodDTO;
import in.wynk.payment.dto.response.WalletCardSupportingDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class Card extends AbstractPaymentMethodDTO {
    private WalletCardSupportingDetails supportingDetails;
}

package in.wynk.payment.dto.response.payu;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.CardDetails;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Getter
@SuperBuilder
@AnalysedEntity
public class UserCardDetails extends AbstractPaymentDetails {
    private Map<String, CardDetails> cards;
}

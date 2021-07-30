package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Getter
@SuperBuilder
@AnalysedEntity
public class CombinedPaymentDetailsResponse extends AbstractPaymentDetails {
    private Map<String, Map<String, AbstractPaymentDetails>> details;
}
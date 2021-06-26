package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.IPurchaseDetails;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
public class DefaultChargingRequest<T extends IPurchaseDetails> extends AbstractChargingRequest<T> {

}

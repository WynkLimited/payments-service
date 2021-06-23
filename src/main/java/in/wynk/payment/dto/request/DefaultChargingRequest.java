package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
public class DefaultChargingRequest<T extends AbstractChargingRequest.IChargingDetails> extends AbstractChargingRequest<T> {

}

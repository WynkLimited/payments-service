package in.wynk.payment.dto.response.presentation.card;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractCardChargingResponse extends AbstractChargingResponse {
}

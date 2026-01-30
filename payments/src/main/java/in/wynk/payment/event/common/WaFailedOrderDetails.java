package in.wynk.payment.event.common;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("FAILURE")
public class WaFailedOrderDetails extends AbstractWaOrderDetails {
    private String errorCode;
    private String errorMessage;

}

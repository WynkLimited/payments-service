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
@JsonTypeName("SUCCESS")
public class WaOrderDetails extends AbstractWaOrderDetails {
    private String code;
    private String pgCode;

    private double amount;
    private double discount;
    private double mandateAmount;

    private boolean trial;
    private boolean mandate;

    private TaxDetails taxDetails;
}

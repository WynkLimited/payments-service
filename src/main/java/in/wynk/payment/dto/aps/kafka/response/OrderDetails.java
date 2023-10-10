package in.wynk.payment.dto.aps.kafka.response;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("in_progress")
public class OrderDetails extends AbstractOrderDetails {
    private String id;
    private String code;
    private String pgCode;

    private int amount;
    private int discount;
    private int mandateAmount;

    private boolean trial;
    private boolean mandate;

    private TaxDetails taxDetails;
}
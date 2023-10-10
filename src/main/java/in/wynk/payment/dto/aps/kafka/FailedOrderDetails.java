package in.wynk.payment.dto.aps.kafka;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.aps.kafka.response.AbstractOrderDetails;
import lombok.AllArgsConstructor;
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
@JsonTypeName("failure")
public class FailedOrderDetails extends AbstractOrderDetails {
    private String errorCode;
    private String errorMessage;

}

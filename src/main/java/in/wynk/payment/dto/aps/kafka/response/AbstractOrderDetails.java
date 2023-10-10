package in.wynk.payment.dto.aps.kafka.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.aps.kafka.FailedOrderDetails;
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
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "status",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderDetails.class, name = "in_progress"),
        @JsonSubTypes.Type(value = FailedOrderDetails.class, name = "failure")
})
public abstract class AbstractOrderDetails {
    private String event;
    private String status;
}

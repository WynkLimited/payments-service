package in.wynk.payment.event.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "status",
        visible = true,
        defaultImpl = OrderDetails.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderDetails.class, name = "SUCCESS"),
        @JsonSubTypes.Type(value = FailedOrderDetails.class, name = "FAILURE")
})
public abstract class AbstractOrderDetails {
    private String id;
    private String event;
    private String status;
}

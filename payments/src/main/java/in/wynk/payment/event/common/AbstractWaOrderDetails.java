package in.wynk.payment.event.common;

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
        defaultImpl = WaOrderDetails.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WaOrderDetails.class, name = "SUCCESS"),
        @JsonSubTypes.Type(value = WaFailedOrderDetails.class, name = "FAILURE")
})
public abstract class AbstractWaOrderDetails {
    private String id;
    private String event;
    private String status;
}

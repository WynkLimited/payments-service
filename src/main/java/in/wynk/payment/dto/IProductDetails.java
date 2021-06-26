package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.common.constant.BaseConstants;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlanDetails.class, name = BaseConstants.PLAN),
        @JsonSubTypes.Type(value = PointDetails.class, name = BaseConstants.POINT)
})
public interface IProductDetails {
    boolean isAutoRenew();
    String getType();
}
package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IChargingDetails.IPageUrlDetails;
import in.wynk.subscription.common.request.SessionRequest;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BestValuePlanResponse  implements Serializable {
    private String planId;
    private BestValuePlanPurchaseRequest bestValuePlanPurchaseRequest;
}

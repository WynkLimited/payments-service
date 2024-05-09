package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BestValuePlanResponse implements Serializable {
    private String planId;
    private BestValuePlanPurchaseRequest bestValuePlanPurchaseRequest;
}

package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TrialPlanEligibilityRequest {
    private int planId;
    private String service;
    private IUserDetails userDetails;
    private IAppDetails appDetails;
}

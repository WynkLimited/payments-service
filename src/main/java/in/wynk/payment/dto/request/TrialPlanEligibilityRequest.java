package in.wynk.payment.dto.request;

import in.wynk.payment.dto.IAppDetails;
import in.wynk.payment.dto.IUserDetails;
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

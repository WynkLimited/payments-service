package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SelectivePlanEligibilityRequest {
    private int planId;
    private String service;
    private IAppDetails appDetails;
    private IUserDetails userDetails;
}
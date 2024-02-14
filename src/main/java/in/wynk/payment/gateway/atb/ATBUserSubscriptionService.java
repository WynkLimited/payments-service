package in.wynk.payment.gateway.atb;

import in.wynk.vas.client.dto.atb.UserSubscriptionStatusResponse;

/**
 * @author Nishesh Pandey
 */
public interface ATBUserSubscriptionService {
    UserSubscriptionStatusResponse getUserSubscriptionDetails (String si, String txnId);
}

package in.wynk.payment.service;

import in.wynk.payment.dto.UserPlanMapping;

public interface IReceiptDetailService {

    UserPlanMapping getUserPlanMapping(String requestPayload);

    boolean isNotificationEligible(String requestPayload);

}

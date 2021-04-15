package in.wynk.payment.service;

import in.wynk.payment.dto.UserPlanMapping;

public interface IReceiptDetailService<T> {

    UserPlanMapping<T> getUserPlanMapping(String requestPayload);

    boolean isNotificationEligible(String requestPayload);

}

package in.wynk.payment.service;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.dto.DecodedNotificationWrapper;
import in.wynk.payment.dto.IAPNotification;
import in.wynk.payment.dto.UserPlanMapping;

public interface IReceiptDetailService<T, R extends IAPNotification> {

    UserPlanMapping<T> getUserPlanMapping(DecodedNotificationWrapper<R> wrapper);

    DecodedNotificationWrapper<R> isNotificationEligible(String requestPayload);

    PaymentEvent getPaymentEvent(DecodedNotificationWrapper<R> wrapper, String type);
}

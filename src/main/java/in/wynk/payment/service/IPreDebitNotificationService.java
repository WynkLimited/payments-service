package in.wynk.payment.service;

import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.PreDebitRequest;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;

/**
 * @author Nishesh Pandey
 */
public interface IPreDebitNotificationService {
    AbstractPreDebitNotificationResponse notify (PreDebitRequest request);
}

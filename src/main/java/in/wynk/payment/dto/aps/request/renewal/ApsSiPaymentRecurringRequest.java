package in.wynk.payment.dto.aps.request.renewal;

import in.wynk.payment.dto.aps.common.SiPaymentInfo;
import in.wynk.payment.dto.aps.common.UserInfo;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class ApsSiPaymentRecurringRequest<T extends SiPaymentInfo> {
    private String orderId; //transactionId
    private T siPaymentInfo;
    private UserInfo userInfo;
}
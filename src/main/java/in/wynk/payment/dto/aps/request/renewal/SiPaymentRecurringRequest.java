package in.wynk.payment.dto.aps.request.renewal;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.aps.common.SiPaymentInfo;
import in.wynk.payment.dto.aps.common.UserInfo;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class SiPaymentRecurringRequest<T extends SiPaymentInfo> {
    private T siPaymentInfo;
    @JsonProperty("orderId")
    private String transactionId;
}
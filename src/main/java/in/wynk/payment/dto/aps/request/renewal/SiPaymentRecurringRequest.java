package in.wynk.payment.dto.aps.request.renewal;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.aps.common.SiPaymentInfo;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SiPaymentRecurringRequest<T extends SiPaymentInfo> {
    private T siPaymentInfo;
    private String orderId;
}
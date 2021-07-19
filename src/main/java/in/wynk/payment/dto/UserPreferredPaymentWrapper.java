package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
@JsonInclude(JsonInclude.Include.ALWAYS)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UserPreferredPaymentWrapper {
    private UserPreferredPayment userPreferredPayment;
    private int planId;
    private String couponId;
}

package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UserPreferredPaymentWrapper {
    private UserPreferredPayment userPreferredPayment;
    private int planId;
    private String couponId;
}

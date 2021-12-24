package in.wynk.payment.service.impl;

import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class PreferredPaymentDetailsRequest<T extends IProductDetails> extends AbstractPreferredPaymentDetailsRequest<T> {
    private UserPreferredPayment preferredPayment;
    private String clientAlias;
    private String si;
}

package in.wynk.payment.service.impl;

import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PreferredPaymentDetailsRequest<T extends IProductDetails> extends AbstractPreferredPaymentDetailsRequest<T> {
    private UserPreferredPayment preferredPayment;
}

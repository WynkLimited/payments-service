package in.wynk.payment.service.impl;

import in.wynk.payment.core.dao.entity.IProductDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class AbstractPreferredPaymentDetailsRequest<T extends IProductDetails> {
    private T productDetails;
}

package in.wynk.payment.service.impl;

import in.wynk.payment.core.dao.entity.IProductDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public abstract class AbstractPreferredPaymentDetailsRequest<T extends IProductDetails> {
    private String couponId;
    private T productDetails;
}

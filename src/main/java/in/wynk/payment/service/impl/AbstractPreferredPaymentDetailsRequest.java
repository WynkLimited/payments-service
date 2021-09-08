package in.wynk.payment.service.impl;

import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.payment.core.dao.entity.IProductDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

import static in.wynk.common.constant.CacheBeanNameConstants.COUPON;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractPreferredPaymentDetailsRequest<T extends IProductDetails> {

    @MongoBaseEntityConstraint(beanName = COUPON)
    private String couponId;

    @Valid
    private T productDetails;

}
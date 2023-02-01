package in.wynk.payment.utils;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.enums.DiscountType;
import in.wynk.coupon.core.service.CouponCachingService;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.service.PaymentCachingService;

import java.util.Objects;
import java.util.Optional;

import static in.wynk.common.constant.BaseConstants.PLAN;

public class DiscountUtils {
    public static double compute(String couponId, IProductDetails productDetails) {
        final CouponCachingService couponCachingService = BeanLocatorFactory.getBean(CouponCachingService.class);
        final PaymentCachingService paymentCachingService = BeanLocatorFactory.getBean(PaymentCachingService.class);
        final double productPrice = productDetails.getType() == PLAN ? paymentCachingService.getPlan(productDetails.getId()).getFinalPrice() : paymentCachingService.getItem(productDetails.getId()).getPrice();
        return Optional.ofNullable(couponId).map(couponCachingService::get).map(coupon -> (coupon.getDiscountType()== DiscountType.FLAT) ? (productPrice - coupon.getDiscount()) : (productPrice- (productPrice * coupon.getDiscount()/ 100))).orElse(productPrice);
    }
}
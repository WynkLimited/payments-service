package in.wynk.payment.utils;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.service.ICouponCacheService;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.service.PaymentCachingService;

import java.util.Objects;
import java.util.Optional;

import static in.wynk.common.constant.BaseConstants.PLAN;

public class DiscountUtils {

    public static double compute(String couponId, IProductDetails productDetails) {
        final PaymentCachingService paymentCache = BeanLocatorFactory.getBean(PaymentCachingService.class);
        final ICouponCacheService couponCache = BeanLocatorFactory.getBean(ICouponCacheService.class);
        final double productPrice = productDetails.getType() == PLAN ? paymentCache.getPlan(productDetails.getId()).getFinalPrice() : paymentCache.getItem(productDetails.getId()).getPrice();
        return Optional.ofNullable(couponId).filter(Objects::nonNull).map(couponCache::getCouponById).filter(Objects::nonNull).map(coupon -> (productPrice - (productPrice * coupon.getDiscountPercent() / 100))).orElse(productPrice);
    }

}

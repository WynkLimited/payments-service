package in.wynk.payment.service.impl;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.coupon.core.dao.entity.CouponCodeLink;
import in.wynk.coupon.core.service.CouponCachingService;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.coupon.core.service.impl.CouponCodeLinkServiceImpl;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.request.AbstractTransactionInitRequest;
import in.wynk.payment.dto.request.PlanTransactionInitRequest;
import in.wynk.payment.dto.request.PointTransactionInitRequest;
import in.wynk.payment.service.IPricingManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicPricingManager implements IPricingManager {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;
    private final CouponCachingService couponCachingService;
    private final CouponCodeLinkServiceImpl couponCodeLinkService;

    @Override
    public void computePriceAndApplyDiscount(AbstractTransactionInitRequest request) {
        if (request instanceof PlanTransactionInitRequest) {
            final PlanTransactionInitRequest nativeRequest = (PlanTransactionInitRequest) request;
            final PlanDTO selectedPlan = cachingService.getPlan(nativeRequest.getPlanId());
            if (nativeRequest.getEvent() != PaymentEvent.RENEW) {
                if (nativeRequest.isAutoRenewOpted()) nativeRequest.setEvent(PaymentEvent.SUBSCRIBE);
                if (nativeRequest.isTrialOpted()) {
                    nativeRequest.setAmount(cachingService.getPlan(selectedPlan.getLinkedFreePlanId()).getFinalPrice());
                    nativeRequest.setEvent(PaymentEvent.TRIAL_SUBSCRIPTION);
                    return;
                }
            }
            nativeRequest.setAmount(selectedPlan.getFinalPrice());
            if (EnumSet.of(PaymentCode.ITUNES, PaymentCode.AMAZON_IAP).contains(request.getPaymentCode()))
                couponManager.applyCoupon(nativeRequest.getUid(), nativeRequest.getCouponId());
            if (selectedPlan.getPlanType() == PlanType.FREE_TRIAL) return;
        } else {
            final PointTransactionInitRequest pointRequest = (PointTransactionInitRequest) request;
            pointRequest.setAmount(Optional.ofNullable(cachingService.getItem(pointRequest.getItemId())).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY106)).getPrice());
        }
        if (StringUtils.isNotEmpty(request.getCouponId())) {
            final CouponCodeLink codeLink = couponCodeLinkService.fetchCouponCodeLink(request.getCouponId());
            if (Objects.nonNull(codeLink)) {
                final Double discountPercent = couponCachingService.get(codeLink.getCouponId()).getDiscountPercent();
                request.setAmount(Double.parseDouble(new DecimalFormat("#.00").format(request.getAmount() * (1 - discountPercent / 100))));
                request.setDiscount(discountPercent);
            }
        }
    }
}
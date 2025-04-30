package in.wynk.payment.service.impl;

import in.wynk.common.dto.GeoLocation;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.coupon.core.dao.entity.Coupon;
import in.wynk.coupon.core.dao.entity.CouponCodeLink;
import in.wynk.coupon.core.enums.DiscountType;
import in.wynk.coupon.core.service.CouponCachingService;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.coupon.core.service.impl.CouponCodeLinkServiceImpl;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.UserDetails;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.request.AbstractTransactionInitRequest;
import in.wynk.payment.dto.request.PlanTransactionInitRequest;
import in.wynk.payment.dto.request.PointTransactionInitRequest;
import in.wynk.payment.service.IPricingManager;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import in.wynk.subscription.common.request.UserPersonalisedPlanRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Locale;
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
    private final ISubscriptionServiceManager subscriptionManager;

    @Override
    public void computePriceAndApplyDiscount(AbstractTransactionInitRequest request) {
        if (request instanceof PlanTransactionInitRequest) {
            final PlanTransactionInitRequest nativeRequest = (PlanTransactionInitRequest) request;
            PlanDTO selectedPlan = cachingService.getPlan(nativeRequest.getPlanId());
            if (Objects.nonNull(nativeRequest.getUserDetails()) && Objects.nonNull(nativeRequest.getAppDetails()) && Objects.nonNull(nativeRequest.getGeoDetails()))
                selectedPlan = subscriptionManager.getUserPersonalisedPlanOrDefault(UserPersonalisedPlanRequest.builder().userDetails(((UserDetails) nativeRequest.getUserDetails()).toUserDetails(request.getUid())).appDetails(((AppDetails) nativeRequest.getAppDetails()).toAppDetails()).geoDetails((GeoLocation) nativeRequest.getGeoDetails()).planId(nativeRequest.getPlanId()).build(), selectedPlan);
            if (nativeRequest.isAutoRenewOpted() || nativeRequest.isMandate()) nativeRequest.setMandateAmount(selectedPlan.getMandateAmount());
            if(nativeRequest.getEvent() == PaymentEvent.RENEW ){
                nativeRequest.setAmount(selectedPlan.getRenewalAmount());
            }
            if (nativeRequest.getEvent() != PaymentEvent.RENEW) {
                if (nativeRequest.isMandate()) {
                    nativeRequest.setMandateAmount(selectedPlan.getMandateAmount());
                    nativeRequest.setEvent(PaymentEvent.MANDATE);
                    nativeRequest.setAmount(PaymentConstants.MANDATE_FLOW_AMOUNT);
                    return;
                }
                else if (nativeRequest.isAutoRenewOpted()) {
                    nativeRequest.setEvent(PaymentEvent.SUBSCRIBE);
                    nativeRequest.setAmount(selectedPlan.getFinalPrice());
                }
                else if (nativeRequest.isTrialOpted()) {
                    nativeRequest.setMandateAmount(selectedPlan.getMandateAmount());
                    nativeRequest.setAmount(PaymentConstants.MANDATE_FLOW_AMOUNT);
                    nativeRequest.setEvent(PaymentEvent.TRIAL_SUBSCRIPTION);
                    return;
                }
                else {
                    nativeRequest.setAmount(selectedPlan.getFinalPrice());
                }
            }
            if (Arrays.asList(PaymentConstants.ITUNES, PaymentConstants.AMAZON_IAP, PaymentConstants.GOOGLE_IAP).contains(request.getPaymentGateway().getId())) couponManager.applyCoupon(nativeRequest.getUid(), nativeRequest.getCouponId());
            if (selectedPlan.getPlanType() == PlanType.FREE_TRIAL) return;
        } else {
            final PointTransactionInitRequest pointRequest = (PointTransactionInitRequest) request;
            if (request.getAmount() != 0.0) {
                pointRequest.setAmount(request.getAmount());
            } else {
                pointRequest.setAmount(Optional.ofNullable(cachingService.getItem(pointRequest.getItemId())).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY106)).getPrice());
            }
        }
        if (StringUtils.isNotEmpty(request.getCouponId())) {
            final CouponCodeLink codeLink = couponCodeLinkService.fetchCouponCodeLink(request.getCouponId().toUpperCase(Locale.ROOT));
            if (Objects.nonNull(codeLink)) {
                String couponCode = codeLink.getCouponId();
                Coupon coupon = couponCachingService.get(codeLink.getCouponId());
                if (!coupon.isCaseSensitive()) {
                    couponCode = codeLink.getCouponId().toUpperCase(Locale.ROOT);
                }
                final Double discount = couponCachingService.get(couponCode).getDiscount();
                request.setAmount(Double.parseDouble(new DecimalFormat("#.00").format(coupon.getDiscountType()== DiscountType.FLAT ? request.getAmount()-discount : request.getAmount() *(1 - discount/100))));
                request.setDiscount(discount);
            }
        }
    }
}
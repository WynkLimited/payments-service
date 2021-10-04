package in.wynk.payment.service.impl;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.coupon.core.constant.CouponProvisionState;
import in.wynk.coupon.core.constant.ProvisionSource;
import in.wynk.coupon.core.dto.CouponDTO;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.request.AbstractTransactionInitRequest;
import in.wynk.payment.dto.request.PlanTransactionInitRequest;
import in.wynk.payment.dto.request.PointTransactionInitRequest;
import in.wynk.payment.dto.request.SelectivePlanEligibilityRequest;
import in.wynk.payment.service.IPricingManager;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import in.wynk.subscription.common.response.SelectivePlansComputationResponse;
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
    private final ISubscriptionServiceManager subscriptionService;

    @Override
    public void computePriceAndApplyDiscount(AbstractTransactionInitRequest request) {
        final Optional<CouponDTO> couponOptional;
        if (request instanceof PlanTransactionInitRequest) {
            final PlanTransactionInitRequest nativeRequest = (PlanTransactionInitRequest) request;
            final PlanDTO selectedPlan = cachingService.getPlan(nativeRequest.getPlanId());
            final String service = selectedPlan.getService();
            if (nativeRequest.getEvent() != PaymentEvent.RENEW) {
                if (nativeRequest.isAutoRenewOpted()) {
                    request.setEvent(PaymentEvent.SUBSCRIBE);
                }
                if (nativeRequest.isTrialOpted()) {
                    final int trialPlanId = selectedPlan.getLinkedFreePlanId();
                    final SelectivePlansComputationResponse trialEligibilityResponse = subscriptionService.compute(SelectivePlanEligibilityRequest.builder().planId(trialPlanId).service(service).appDetails(nativeRequest.getAppDetails()).userDetails(nativeRequest.getUserDetails()).build());
                    if (Objects.nonNull(trialEligibilityResponse) && trialEligibilityResponse.getEligiblePlans().contains(trialPlanId)) {
                        final PlanDTO trialPlan = cachingService.getPlan(trialPlanId);
                        nativeRequest.setAmount(trialPlan.getFinalPrice());
                        request.setEvent(PaymentEvent.TRIAL_SUBSCRIPTION);
                        return;
                    }
                }
            }
            nativeRequest.setAmount(selectedPlan.getFinalPrice());
            couponOptional = optionalPlanDiscount(request.getCouponId(), request.getMsisdn(), request.getUid(), service, request.getPaymentCode(), selectedPlan);
        } else {
            final PointTransactionInitRequest pointRequest = (PointTransactionInitRequest) request;
            final ItemDTO itemDTO = Optional.ofNullable(cachingService.getItem(pointRequest.getItemId())).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY106));
            final String service = itemDTO.getService();
            request.setAmount(itemDTO.getPrice());
            couponOptional = optionalItemDiscount(request.getCouponId(), request.getMsisdn(), request.getUid(), service, itemDTO.getId(), request.getPaymentCode());
        }
        couponOptional.ifPresent(couponDTO -> {
            final double totalAmount = request.getAmount();
            final double finalAmount = getFinalAmount(totalAmount, couponDTO);
            request.setAmount(finalAmount);
            request.setDiscount(couponDTO.getDiscountPercent());
        });
    }

    private Optional<CouponDTO> optionalPlanDiscount(String couponId, String msisdn, String uid, String service, PaymentCode paymentCode, PlanDTO selectedPlan) {
        if (!StringUtils.isEmpty(couponId) && selectedPlan.getPlanType() != PlanType.FREE_TRIAL) {
            CouponProvisionRequest couponProvisionRequest = CouponProvisionRequest.builder().couponCode(couponId).msisdn(msisdn).service(service).paymentCode(paymentCode.getCode()).selectedPlan(selectedPlan).uid(uid).source(ProvisionSource.MANAGED).build();
            final CouponResponse couponResponse;
            if (EnumSet.of(PaymentCode.ITUNES, PaymentCode.AMAZON_IAP).contains(paymentCode)) {
                couponResponse = couponManager.applyCoupon(couponProvisionRequest);
            } else {
                couponResponse = couponManager.evalCouponEligibility(couponProvisionRequest);
            }
            if (couponResponse.getState() != CouponProvisionState.INELIGIBLE) {
                return Optional.of(couponResponse.getCoupon());
            }
        }
        return Optional.empty();
    }

    private Optional<CouponDTO> optionalItemDiscount(String couponId, String msisdn, String uid, String service, String itemId, PaymentCode paymentCode) {
        if (!StringUtils.isEmpty(couponId)) {
            CouponProvisionRequest couponProvisionRequest = CouponProvisionRequest.builder()
                    .couponCode(couponId).msisdn(msisdn).service(service).paymentCode(paymentCode.name()).itemId(itemId).uid(uid).source(ProvisionSource.MANAGED).build();
            CouponResponse couponResponse = couponManager.evalCouponEligibility(couponProvisionRequest);
            if (couponResponse.getState() != CouponProvisionState.INELIGIBLE) {
                return Optional.of(couponResponse.getCoupon());
            }
        }
        return Optional.empty();
    }

    private double getFinalAmount(double totalAmount, CouponDTO coupon) {
        double discount = coupon.getDiscountPercent();
        DecimalFormat df = new DecimalFormat("#.00");
        return Double.parseDouble(df.format(totalAmount - (totalAmount * discount) / 100));
    }

}

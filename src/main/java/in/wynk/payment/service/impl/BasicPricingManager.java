package in.wynk.payment.service.impl;

import in.wynk.coupon.core.constant.CouponProvisionState;
import in.wynk.coupon.core.constant.ProvisionSource;
import in.wynk.coupon.core.dto.CouponDTO;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.AbstractTransactionInitRequest;
import in.wynk.payment.dto.request.PlanTransactionInitRequest;
import in.wynk.payment.dto.request.PointTransactionInitRequest;
import in.wynk.payment.service.IPricingManager;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
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
        String itemId = null;
        String service = StringUtils.EMPTY;
        PlanDTO selectedPlan = null;
        if (request instanceof PlanTransactionInitRequest) {
            PlanTransactionInitRequest nativeRequest = (PlanTransactionInitRequest) request;
            selectedPlan = cachingService.getPlan(nativeRequest.getPlanId());
            service = selectedPlan.getService();
            nativeRequest.setAmount(selectedPlan.getFinalPrice());
            if (nativeRequest.isTrialOpted()) {
                // TODO:: eligibility handling
//                PlanEligibilityResponse trialResponse = subscriptionService.evaluateEligibility(selectedPlan.getLinkedFreePlanId(), request.getUid());
//                if (trialResponse.isEligible()) {
                // TODO:: return without apply discount if trial subscription is found

//                    selectedPlan = cachingService.getPlan(selectedPlan.getLinkedFreePlanId());
//                    request.setEvent(PaymentEvent.TRIAL_SUBSCRIPTION);
                // return
//                }
            }
        } else {
            PointTransactionInitRequest pointRequest = (PointTransactionInitRequest) request;
            itemId = pointRequest.getItemId();
        }
        optionalDiscount(request.getCouponId(), request.getMsisdn(), request.getUid(), service, itemId, request.getPaymentCode(), selectedPlan).ifPresent(couponDTO -> {
            final double totalAmount = request.getAmount();
            final double finalAmount = getFinalAmount(totalAmount, couponDTO);
            request.setAmount(finalAmount);
            request.setDiscount(couponDTO.getDiscountPercent());
        });
    }

    private Optional<CouponDTO> optionalDiscount(String couponId, String msisdn, String uid, String service, String itemId, PaymentCode paymentCode, PlanDTO selectedPlan) {
        if (!StringUtils.isEmpty(couponId) && selectedPlan.getPlanType() != PlanType.FREE_TRIAL) {
            CouponProvisionRequest couponProvisionRequest = CouponProvisionRequest.builder()
                    .couponCode(couponId).msisdn(msisdn).service(service).paymentCode(paymentCode.getCode()).selectedPlan(selectedPlan).itemId(itemId).uid(uid).source(ProvisionSource.MANAGED).build();
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

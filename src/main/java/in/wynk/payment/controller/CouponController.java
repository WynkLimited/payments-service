package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponse;
import in.wynk.coupon.core.constant.ProvisionSource;
import in.wynk.coupon.core.dto.CouponDTO;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("wynk/v1/coupon")
public class CouponController {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;

    public CouponController(ICouponManager couponManager, PaymentCachingService cachingService) {
        this.couponManager = couponManager;
        this.cachingService = cachingService;
    }

    @GetMapping("/apply/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "applyCoupon")
    public WynkResponse<CouponResponse> applyCoupon(@PathVariable String sid, @RequestParam String couponCode, @RequestParam(defaultValue = "0") Integer planId, @RequestParam(defaultValue = "") String itemId) {
        String uid = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.UID);
        String msisdn = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.MSISDN);
        String service = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.SERVICE);
        AnalyticService.update(SessionKeys.SELECTED_PLAN_ID, planId);
        AnalyticService.update(SessionKeys.COUPON_CODE, couponCode);
        AnalyticService.update(SessionKeys.SERVICE, service);
        CouponProvisionRequest.CouponProvisionRequestBuilder builder = CouponProvisionRequest.builder().uid(uid).msisdn(msisdn).itemId(itemId).couponCode(couponCode).service(service).source(ProvisionSource.UNMANAGED);
        if (StringUtils.isNotEmpty(itemId)) {
            builder.itemId(itemId);
        } else {
            PlanDTO selectedPlan = cachingService.getPlan(planId);
            builder.selectedPlan(selectedPlan);
        }
        CouponResponse response = couponManager.applyCoupon(builder.build());
        AnalyticService.update(response);
        return WynkResponse.<CouponResponse>builder().body(response).build();
    }

    @DeleteMapping("/remove/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "removeCoupon")
    public WynkResponse<CouponResponse> removeCoupon(@PathVariable String sid, @RequestParam String couponCode) {
        String uid = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.UID);
        AnalyticService.update(SessionKeys.COUPON_CODE, couponCode);
        CouponResponse response = couponManager.removeCoupon(uid, couponCode);
        AnalyticService.update(response);
        return WynkResponse.<CouponResponse>builder().body(response).build();
    }

    @GetMapping("/eligibility/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "eligibleCoupons")
    public WynkResponse<List<CouponDTO>> getEligibleCoupons(@PathVariable String sid, @RequestParam Integer planId) {
        PlanDTO planDTO = cachingService.getPlan(planId);
        String uid = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.UID);
        String msisdn = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.MSISDN);
        AnalyticService.update(SessionKeys.SELECTED_PLAN_ID, planId);
        CouponProvisionRequest request = CouponProvisionRequest.builder().uid(uid).msisdn(msisdn).selectedPlan(planDTO).source(ProvisionSource.UNMANAGED).build();
        List<CouponDTO> eligibleCoupons = couponManager.getEligibleCoupons(request);
        AnalyticService.update(eligibleCoupons);
        return WynkResponse.<List<CouponDTO>>builder().body(eligibleCoupons).build();
    }

}

package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.coupon.core.constant.ProvisionSource;
import in.wynk.coupon.core.dto.CouponDTO;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.common.constant.CacheBeanNameConstants.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/v1/coupon")
public class CouponController {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;

    @GetMapping("/apply/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "applyCoupon")
    public WynkResponse<CouponResponse> applyCoupon(@PathVariable String sid, @RequestParam String couponCode, @RequestParam(required = false) @MongoBaseEntityConstraint(beanName = PLAN_DTO) Integer planId, @RequestParam(required = false) @MongoBaseEntityConstraint(beanName = ITEM_DTO) String itemId ) {
        LoadClientUtils.loadClient(false);
        String uid = SessionContextHolder.<SessionDTO>getBody().get(UID);
        String msisdn = SessionContextHolder.<SessionDTO>getBody().get(MSISDN);
        String service = SessionContextHolder.<SessionDTO>getBody().get(SERVICE);
        GeoLocation geoLocation= SessionContextHolder.<SessionDTO>getBody().get(GEO_LOCATION);
        AnalyticService.update(PLAN_ID, planId);
        AnalyticService.update(COUPON_CODE, couponCode);
        AnalyticService.update(SERVICE, service);
        CouponProvisionRequest.CouponProvisionRequestBuilder builder = CouponProvisionRequest.builder().uid(uid).msisdn(msisdn).itemId(itemId).couponCode(couponCode).geoLocation(geoLocation).service(service).source(ProvisionSource.UNMANAGED);
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
    public WynkResponse<CouponResponse> removeCoupon(@PathVariable String sid, @RequestParam @MongoBaseEntityConstraint(beanName = COUPON) String couponCode) {
        LoadClientUtils.loadClient(false);
        String uid = SessionContextHolder.<SessionDTO>getBody().get(UID);
        AnalyticService.update(COUPON_CODE, couponCode);
        CouponResponse response = couponManager.removeCoupon(uid, couponCode);
        AnalyticService.update(response);
        return WynkResponse.<CouponResponse>builder().body(response).build();
    }

    @GetMapping("/eligibility/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "eligibleCoupons")
    public WynkResponse<List<CouponDTO>> getEligibleCoupons(@PathVariable String sid, @RequestParam @MongoBaseEntityConstraint(beanName = PLAN_DTO) Integer planId) {
        LoadClientUtils.loadClient(false);
        PlanDTO planDTO = cachingService.getPlan(planId);
        String uid = SessionContextHolder.<SessionDTO>getBody().get(UID);
        String msisdn = SessionContextHolder.<SessionDTO>getBody().get(MSISDN);
        AnalyticService.update(PLAN_ID, planId);
        CouponProvisionRequest request = CouponProvisionRequest.builder().uid(uid).msisdn(msisdn).selectedPlan(planDTO).source(ProvisionSource.UNMANAGED).build();
        List<CouponDTO> eligibleCoupons = couponManager.getEligibleCoupons(request);
        AnalyticService.update(eligibleCoupons);
        return WynkResponse.<List<CouponDTO>>builder().body(eligibleCoupons).build();
    }

}
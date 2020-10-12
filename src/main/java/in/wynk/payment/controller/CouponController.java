package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.coupon.core.constant.ProvisionSource;
import in.wynk.coupon.core.dao.entity.Coupon;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("wynk/v1/coupon")
public class CouponController {

    @Autowired
    private ICouponManager couponManager;
    @Autowired
    private PaymentCachingService cachingService;

    @GetMapping("/apply/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "applyCoupon")
    public ResponseEntity<CouponResponse> applyCoupon(@PathVariable String sid, @RequestParam String couponCode, @RequestParam Integer planId, @RequestParam String itemId) {
        String uid = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.UID);
        String msisdn = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.MSISDN);
        String service = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.SERVICE);
        AnalyticService.update(SessionKeys.SELECTED_PLAN_ID, planId);
        AnalyticService.update(SessionKeys.COUPON_ID, couponCode);
        AnalyticService.update(SessionKeys.SERVICE, couponCode);
        CouponProvisionRequest.CouponProvisionRequestBuilder builder = CouponProvisionRequest.builder().uid(uid).msisdn(msisdn).itemId(itemId).couponCode(couponCode).service(service).source(ProvisionSource.UNMANAGED);
        if(StringUtils.isNotEmpty(itemId)) {
            builder.itemId(itemId);
        } else {
            PlanDTO selectedPlan = cachingService.getPlan(planId);
            builder.selectedPlan(selectedPlan);
        }
        CouponResponse couponResponse = couponManager.applyCoupon(builder.build());
        AnalyticService.update(couponResponse);
        return new ResponseEntity<>(couponResponse, HttpStatus.OK);
    }

    @DeleteMapping("/remove/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "removeCoupon")
    public ResponseEntity<CouponResponse> removeCoupon(@PathVariable String sid, @RequestParam String couponCode) {
        String uid = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.UID);
        AnalyticService.update(SessionKeys.COUPON_ID, couponCode);
        CouponResponse response = couponManager.removeCoupon(uid, couponCode);
        AnalyticService.update(response);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/eligibility/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "eligibleCoupons")
    public ResponseEntity<List<Coupon>> getEligibleCoupons(@PathVariable String sid, @RequestParam Integer planId) {
        PlanDTO planDTO = cachingService.getPlan(planId);
        String uid = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.UID);
        String msisdn = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.MSISDN);
        AnalyticService.update(SessionKeys.SELECTED_PLAN_ID, planId);
        CouponProvisionRequest request = CouponProvisionRequest.builder().uid(uid).msisdn(msisdn).selectedPlan(planDTO).source(ProvisionSource.UNMANAGED).build();
        List<Coupon> eligibleCoupons = couponManager.getEligibleCoupons(request);
        AnalyticService.update(eligibleCoupons);
        return new ResponseEntity<>(eligibleCoupons, HttpStatus.OK);
    }

}

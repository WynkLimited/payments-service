package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.coupon.core.constant.ProvisionSource;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<CouponResponse> applyCoupon(@PathVariable String sid, @RequestParam String couponCode, @RequestParam Integer planId) {
        PlanDTO planDTO = cachingService.getPlan(planId);
        String uid = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.UID);
        String msisdn = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.MSISDN);
        AnalyticService.update(SessionKeys.UID, uid);
        AnalyticService.update(SessionKeys.MSISDN, msisdn);
        AnalyticService.update(SessionKeys.SELECTED_PLAN_ID, planId);
        AnalyticService.update(SessionKeys.COUPON_ID, couponCode);
        CouponProvisionRequest request = CouponProvisionRequest.builder().uid(uid).msisdn(msisdn).couponCode(couponCode).selectedPlan(planDTO).source(ProvisionSource.UNMANAGED).build();
        CouponResponse couponResponse = couponManager.applyCoupon(request);
        return new ResponseEntity<>(couponResponse, HttpStatus.OK);
    }

    @DeleteMapping("/remove/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "removeCoupon")
    public ResponseEntity<CouponResponse> removeCoupon(@PathVariable String sid, @RequestParam String couponCode) {
        String uid = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.UID);
        AnalyticService.update(SessionKeys.UID, uid);
        AnalyticService.update(SessionKeys.COUPON_ID, couponCode);
        return new ResponseEntity<>(couponManager.removeCoupon(uid, couponCode), HttpStatus.OK);
    }

}

package in.wynk.payment.controller;

import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.coupon.core.service.CouponCachingService;
import in.wynk.error.codes.core.service.impl.ErrorCodesCacheServiceImpl;
import in.wynk.payment.service.ItemDtoCachingService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.service.PlanDtoCachingService;
import in.wynk.payment.service.impl.PaymentMethodCachingService;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.wynkservice.api.service.AppIdCachingService;
import in.wynk.wynkservice.api.service.OsCachingService;
import in.wynk.wynkservice.api.service.WynkServiceDetailsCachingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!prod")
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s/v1")
public class CacheRefreshController {

    private final OsCachingService osCachingService;
    private final AppIdCachingService appIdCachingService;
    private final CouponCachingService couponCachingService;
    private final PlanDtoCachingService planDtoCachingService;
    private final ItemDtoCachingService itemDtoCachingService;
    private final PaymentCachingService paymentCachingService;
    private final ErrorCodesCacheServiceImpl errorCodesCacheService;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final ClientDetailsCachingService clientDetailsCachingService;
    private final WynkServiceDetailsCachingService wynkServiceDetailsCachingService;

    @GetMapping("/cache/refresh")
    public void refreshCache() {
        LoadClientUtils.loadClient(true);
        osCachingService.init();
        appIdCachingService.init();
        couponCachingService.init();
        planDtoCachingService.init();
        itemDtoCachingService.init();
        paymentCachingService.init();
        errorCodesCacheService.init();
        paymentMethodCachingService.init();
        clientDetailsCachingService.init();
        wynkServiceDetailsCachingService.init();
    }

}
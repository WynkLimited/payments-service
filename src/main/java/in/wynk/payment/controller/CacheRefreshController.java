package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.payment.service.PaymentCachingService;
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

    private final PaymentCachingService paymentCachingService;

    @GetMapping("/cache/refresh")
    @AnalyseTransaction(name = "refreshCache")
    public void refreshCache() {
        paymentCachingService.init();
    }

}
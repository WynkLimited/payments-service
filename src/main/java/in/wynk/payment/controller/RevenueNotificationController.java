package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.service.IAsyncCallbackService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/v1/callback")
public class RevenueNotificationController {

    private final IAsyncCallbackService asyncCallback;

    @Value("${spring.application.name}")
    private String applicationAlias;

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentServerSyncCallback")
    public WynkResponseEntity<Void> handlePartnerCallback(@PathVariable String partner, @RequestBody String payload) {
        return handleCallbackAsync(partner, applicationAlias, payload);
    }

    @PostMapping("/{partner}/{clientAlias}")
    @AnalyseTransaction(name = "paymentServerSyncCallback")
    public WynkResponseEntity<Void> handlePartnerCallbackWithClientAlias(@PathVariable String partner, @PathVariable String clientAlias, @RequestBody String payload) {
        return handleCallbackAsync(partner, clientAlias, payload);
    }

    @AnalyseTransaction(name = "paymentServerSyncCallback")
    @PostMapping(path = "/{partner}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<Void> handlePartnerCallback(@PathVariable String partner, @RequestParam Map<String, Object> payload) {
        return handleCallbackAsync(partner, applicationAlias, payload);
    }

    @AnalyseTransaction(name = "paymentServerSyncCallback")
    @PostMapping(path = "/{partner}/{clientAlias}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<Void> handlePartnerCallbackWithClientAlias(@PathVariable String partner, @PathVariable String clientAlias, @RequestParam Map<String, Object> payload) {
        return handleCallbackAsync(partner, clientAlias, payload);
    }


    public WynkResponseEntity<Void> handleCallbackAsync(String partner, String clientAlias, Object payload) {
        final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        asyncCallback.handle(MDC.get(REQUEST_ID), clientAlias, partner, payload);
        return WynkResponseEntity.<Void>builder().build();
    }

}
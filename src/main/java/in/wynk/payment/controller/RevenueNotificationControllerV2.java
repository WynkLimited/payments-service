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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/v2/callback")
public class RevenueNotificationControllerV2 {

    private final IAsyncCallbackService asyncCallback;

    @Value("${spring.application.name}")
    private String applicationAlias;
    private static final List<String> RECEIPT_PROCESSING_PAYMENT_CODE = Arrays.asList(ITUNES, AMAZON_IAP, GOOGLE_IAP);

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentServerSyncCallback")
    public WynkResponseEntity<Void> handlePartnerCallback(@RequestHeader HttpHeaders headers, @PathVariable String partner, @RequestBody String payload) {
        return handleCallbackAsync(partner, applicationAlias, headers, payload);
    }

    @PostMapping("/{partner}/{clientAlias}")
    @AnalyseTransaction(name = "paymentServerSyncCallback")
    public WynkResponseEntity<Void> handlePartnerCallbackWithClientAlias(@RequestHeader HttpHeaders headers, @PathVariable String partner, @PathVariable String clientAlias,
                                                                         @RequestBody String payload) {
        return handleCallbackAsync(partner, clientAlias, headers, payload);
    }

    @AnalyseTransaction(name = "paymentServerSyncCallback")
    @PostMapping(path = "/{partner}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<Void> handlePartnerCallback(@RequestHeader HttpHeaders headers, @PathVariable String partner, @RequestParam Map<String, Object> payload) {
        return handleCallbackAsync(partner, applicationAlias, headers, payload);
    }

    @AnalyseTransaction(name = "paymentServerSyncCallback")
    @PostMapping(path = "/{partner}/{clientAlias}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<Void> handlePartnerCallbackWithClientAlias(@RequestHeader HttpHeaders headers, @PathVariable String partner, @PathVariable String clientAlias,
                                                                         @RequestParam Map<String, Object> payload) {
        return handleCallbackAsync(partner, clientAlias, headers, payload);
    }

    public WynkResponseEntity<Void> handleCallbackAsync(String partner, String clientAlias, HttpHeaders headers, Object payload) {
        final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        asyncCallback.handle(MDC.get(REQUEST_ID), clientAlias, partner, headers, payload); //check
        return WynkResponseEntity.<Void>builder().build();
    }

}
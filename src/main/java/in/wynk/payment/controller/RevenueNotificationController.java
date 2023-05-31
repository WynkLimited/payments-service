package in.wynk.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.request.CallbackRequestWrapper;
import in.wynk.payment.dto.request.NotificationRequest;
import in.wynk.payment.service.PaymentManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.wynk.payment.core.constant.PaymentConstants.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/v1/callback")
public class RevenueNotificationController {

    private final Gson gson;
    private final PaymentManager paymentManager;

    private static final List<String> RECEIPT_PROCESSING_PAYMENT_CODE = Arrays.asList(ITUNES, AMAZON_IAP, GOOGLE_IAP);

    @Value("${spring.application.name}")
    private String applicationAlias;

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<Void> handlePartnerCallback(@PathVariable String partner, @RequestBody String payload) {
        return handleCallbackInternal(partner, applicationAlias, payload);
    }

    @PostMapping("/{partner}/{clientAlias}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<Void> handlePartnerCallbackWithClientAlias(@PathVariable String partner, @PathVariable String clientAlias, @RequestBody String payload) {
        return handleCallbackInternal(partner, clientAlias, payload);
    }

    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = "/{partner}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<Void> handlePartnerCallback(@PathVariable String partner, @RequestParam Map<String, Object> payload) {
        return handleCallbackInternal(partner, applicationAlias, payload);
    }

    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = "/{partner}/{clientAlias}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<Void> handlePartnerCallbackWithClientAlias(@PathVariable String partner, @PathVariable String clientAlias, @RequestParam Map<String, Object> payload) {
        return handleCallbackInternal(partner, clientAlias, payload);
    }

    @Async
    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 200))
    public <T> void handleCallbackAsync(String partner, String clientAlias, T payload) {
        if (String.class.isAssignableFrom(payload.getClass())) handleCallback(partner, clientAlias, (String) payload);
        else handleCallback(partner, clientAlias, (Map<String, Object>) payload);
    }

    private <T> WynkResponseEntity<Void> handleCallbackInternal(String partner, String clientAlias, T payload) {
        handleCallbackAsync(partner, clientAlias, payload);
        return WynkResponseEntity.<Void>builder().success(true).build();
    }

    private void handleCallback(String partner, String clientAlias, String payload) {
        final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, payload);
        if (!RECEIPT_PROCESSING_PAYMENT_CODE.contains(paymentGateway.name())) {
            try {
                handleCallback(partner, clientAlias, BeanLocatorFactory.getBean(ObjectMapper.class).readValue(payload, new TypeReference<HashMap<String, Object>>() {
                }));
            } catch (JsonProcessingException e) {
                throw new WynkRuntimeException("Malformed payload is posted", e);
            }
        }
        paymentManager.handleNotification(NotificationRequest.builder().paymentGateway(paymentGateway).payload(payload).clientAlias(clientAlias).build());
    }

    @ClientAware(clientAlias = "#clientAlias")
    private void handleCallback(String partner, String clientAlias, Map<String, Object> payload) {
        final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        paymentManager.handleCallback(CallbackRequestWrapper.builder().paymentGateway(paymentGateway).payload(payload).build());
    }

}
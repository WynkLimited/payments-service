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
import in.wynk.payment.service.PaymentGatewayManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.wynk.payment.core.constant.PaymentConstants.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/v2/callback")
public class RevenueNotificationControllerV2 {

    private final Gson gson;
    private final PaymentGatewayManager paymentGatewayManager;

    @Value("${spring.application.name}")
    private String applicationAlias;
    private static final List<String> RECEIPT_PROCESSING_PAYMENT_CODE = Arrays.asList(ITUNES, AMAZON_IAP, GOOGLE_IAP);

    //This version is for payment refactoring task
    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<Void> handlePartnerCallback(@PathVariable String partner, @RequestBody String payload) {
        return getVoidWynkResponseEntity(partner, applicationAlias, payload);
    }

    //This version is for payment refactoring task
    @PostMapping("/{partner}/{clientAlias}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<Void> handlePartnerCallbackWithClientAlias(@PathVariable String partner, @PathVariable String clientAlias, @RequestBody String payload) {
        return getVoidWynkResponseEntity(partner, clientAlias, payload);
    }

    private WynkResponseEntity<Void> getVoidWynkResponseEntity(String partner, String clientAlias, String payload) {
        final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, payload);
        if (!RECEIPT_PROCESSING_PAYMENT_CODE.contains(paymentGateway.name())) {
            try {
                return handleCallback(partner, BeanLocatorFactory.getBean(ObjectMapper.class).readValue(payload, new TypeReference<HashMap<String, Object>>() {
                }));
            } catch (JsonProcessingException e) {
                throw new WynkRuntimeException("Malformed payload is posted", e);
            }
        }
        return paymentGatewayManager.handleNotification(NotificationRequest.builder().paymentGateway(paymentGateway).payload(payload).clientAlias(clientAlias).build());
    }

    //This version is for payment refactoring task
    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = "/{partner}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<Void> handlePartnerCallback(@PathVariable String partner, @RequestParam Map<String, Object> payload) {
        return handleCallback(partner, payload);
    }

    //This version is for payment refactoring task
    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = "/{partner}/{clientAlias}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<Void> handlePartnerCallbackWithClientAlias(@PathVariable String partner, @PathVariable String clientAlias, @RequestParam Map<String, Object> payload) {
        return handleCallback(partner, payload);
    }

    @ClientAware(clientAlias = "#clientAlias")
    private WynkResponseEntity<Void> handleCallback(String partner, Map<String, Object> payload) {
        final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        paymentGatewayManager.handleCallback(CallbackRequestWrapper.builder().paymentGateway(paymentGateway).payload(payload).build());
        return WynkResponseEntity.<Void>builder().success(true).build();
    }
}
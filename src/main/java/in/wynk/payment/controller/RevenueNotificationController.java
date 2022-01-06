package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.CallbackRequestWrapper;
import in.wynk.payment.dto.request.NotificationRequest;
import in.wynk.payment.service.PaymentManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST_PAYLOAD;

@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/v1/callback")
public class RevenueNotificationController {

    private final Gson gson;
    private final PaymentManager paymentManager;

    @Value("${spring.application.name}")
    private String applicationAlias;

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<Void> handlePartnerCallback(@PathVariable String partner, @RequestBody String payload) {
        return getVoidWynkResponseEntity(partner, applicationAlias, payload);
    }

    @PostMapping("/{partner}/{clientAlias}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<Void> handlePartnerCallbackWithClientAlias(@PathVariable String partner, @PathVariable String clientAlias, @RequestBody String payload) {
        return getVoidWynkResponseEntity(partner, clientAlias, payload);
    }

    private WynkResponseEntity<Void> getVoidWynkResponseEntity(String partner, String clientAlias, String payload) {
        PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, payload);
        return paymentManager.handleNotification(NotificationRequest.builder().paymentCode(paymentCode).payload(payload).clientAlias(clientAlias).build());
    }

    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = "/{partner}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public EmptyResponse handlePartnerCallback(@PathVariable String partner, @RequestParam Map<String, Object> payload) {
        return getEmptyResponse(partner, applicationAlias, payload);
    }

    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = "/{partner}/{clientAlias}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public EmptyResponse handlePartnerCallbackWithClientAlias(@PathVariable String partner, @PathVariable String clientAlias, @RequestParam Map<String, Object> payload) {
        return getEmptyResponse(partner, clientAlias, payload);
    }

    @ClientAware(clientAlias = "#clientAlias")
    private EmptyResponse getEmptyResponse(String partner, String clientAlias, Map<String, Object> payload) {
        final PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        paymentManager.handleCallback(CallbackRequestWrapper.builder().paymentCode(paymentCode).payload(payload).build());
        return EmptyResponse.response();
    }

}
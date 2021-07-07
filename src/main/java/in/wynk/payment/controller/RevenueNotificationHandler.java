package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentCode;
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
public class RevenueNotificationHandler {

    @Value("${spring.application.name}")
    private String applicationAlias;

    private final Gson gson;
    private final PaymentManager paymentManager;

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<Void> handlePartnerCallback(@PathVariable String partner, @RequestBody String payload) {
        PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, payload);
        return paymentManager.handleNotification(NotificationRequest.builder().paymentCode(paymentCode).payload(payload).clientAlias(applicationAlias).build());
    }

    @PostMapping(path = "/{partner}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @AnalyseTransaction(name = "paymentCallback")
    public EmptyResponse handlePartnerCallback(@PathVariable String partner, @RequestParam Map<String, Object> payload) {
        final PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        paymentManager.handleCallback(payload, paymentCode);
        return EmptyResponse.response();
    }

}
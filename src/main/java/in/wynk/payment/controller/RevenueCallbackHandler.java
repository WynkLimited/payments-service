package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.PaymentManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST_PAYLOAD;

@RestController
@RequestMapping("wynk/v1/callback")
public class RevenueCallbackHandler {

    @Value("${spring.application.name}")
    private String applicationAlias;

    private final Gson gson;
    private final PaymentManager paymentManager;

    public RevenueCallbackHandler(Gson gson, PaymentManager paymentManager) {
        this.gson = gson;
        this.paymentManager = paymentManager;
    }

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handlePartnerCallback(@PathVariable String partner, @RequestBody String payload) {
        AnalyticService.update("JAI", "handlePartnerCallback");
        CallbackRequest request = CallbackRequest.builder().body(payload).build();
        PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, payload);
        BaseResponse<?> baseResponse = paymentManager.handleNotification(applicationAlias, request, paymentCode);
        return baseResponse.getResponse();
    }

    @PostMapping(path = "/{partner}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handlePartnerCallbackJSON(@PathVariable String partner, @RequestBody Map<String, Object> payload) {
        AnalyticService.update("JAI", "handlePartnerCallbackJSON");
        CallbackRequest request = CallbackRequest.builder().body(payload).build();
        PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        BaseResponse<?> baseResponse = paymentManager.handleNotification(applicationAlias, request, paymentCode);
        return baseResponse.getResponse();
    }

    @PostMapping(path = "/{partner}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handlePartnerCallbackForm(@PathVariable String partner, @RequestBody Map<String, Object> payload) {
        AnalyticService.update("JAI", "handlePartnerCallbackForm");
        CallbackRequest request = CallbackRequest.builder().body(payload).build();
        PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        BaseResponse<?> baseResponse = paymentManager.handleNotification(applicationAlias, request, paymentCode);
        return baseResponse.getResponse();
    }

}
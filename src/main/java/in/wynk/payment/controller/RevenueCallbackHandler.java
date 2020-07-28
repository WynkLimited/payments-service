package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.core.enums.PaymentCode;
import in.wynk.payment.core.utils.BeanLocatorFactory;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST_PAYLOAD;

@RestController
@RequestMapping("wynk/v1/callback")
public class RevenueCallbackHandler {

    private final Gson gson;

    public RevenueCallbackHandler(Gson gson) {
        this.gson = gson;
    }

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handlePartnerCallback(@PathVariable String partner, @RequestBody Map<String, Object> payload) {
        CallbackRequest request = CallbackRequest.builder().body(payload).build();
        PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentCallbackService.class);
        BaseResponse<?> baseResponse = callbackService.handleCallback(request);
        return baseResponse.getResponse();
    }
}

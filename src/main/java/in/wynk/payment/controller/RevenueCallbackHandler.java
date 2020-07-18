package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.payment.core.constant.ApplicationConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import in.wynk.payment.utils.BeanLocatorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("wynk/v1/callback")
public class RevenueCallbackHandler {

    @Autowired
    private Gson gson;

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handlePartnerCallback(@PathVariable String partner, @RequestBody Map<String, Object> payload) {
        CallbackRequest request = CallbackRequest.builder().body(payload).build();
        PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(ApplicationConstant.REQUEST_PAYLOAD, gson.toJson(payload));
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentCallbackService.class);
        BaseResponse<?> baseResponse = callbackService.handleCallback(request);
        return baseResponse.getResponse();
    }
}

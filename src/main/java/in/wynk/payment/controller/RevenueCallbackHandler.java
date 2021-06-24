package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.service.PaymentManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST_PAYLOAD;

@RestController
@RequiredArgsConstructor
@RequestMapping("wynk/v1/callback")
public class RevenueCallbackHandler {

    @Value("${spring.application.name}")
    private String applicationAlias;

    private final PaymentManager paymentManager;

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handlePartnerCallback(@PathVariable String partner, @RequestBody String payload) {
        CallbackRequest request = CallbackRequest.builder().body(payload).build();
        PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, payload);
        WynkResponseEntity<?> baseResponse = paymentManager.handleNotification(applicationAlias, request, paymentCode);
        return baseResponse;
    }

}

package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.EmptyResponse;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.service.PaymentManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST_PAYLOAD;

@RestController
@RequestMapping("wynk/v1/callback")
public class RevenueNotificationHandler {

    @Autowired
    private PaymentManager paymentManager;
    @Value("${spring.application.name}")
    private String applicationAlias;

    @PostMapping("/{partner}")
    @AnalyseTransaction(name = "paymentCallback")
    public EmptyResponse handlePartnerCallback(@PathVariable String partner, @RequestBody String payload) {
        CallbackRequest request = CallbackRequest.builder().body(payload).build();
        PaymentCode paymentCode = PaymentCode.getFromCode(partner);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, payload);
        return paymentManager.handleNotification(applicationAlias, payload, paymentCode);
    }

}

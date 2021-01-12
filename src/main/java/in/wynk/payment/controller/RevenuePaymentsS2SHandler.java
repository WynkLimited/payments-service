package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.PaymentManager;
import in.wynk.session.aspect.advice.ManageSession;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

@RestController
@RequestMapping("/wynk/s2s/v1")
public class RevenuePaymentsS2SHandler {

    private final PaymentManager paymentManager;

    public RevenuePaymentsS2SHandler(PaymentManager paymentManager) {
        this.paymentManager = paymentManager;
    }

    @ApiOperation("Accepts the receipt of various IAP partners." +
            "\nAn alernate API for old itunes/receipt and /amazon-iap/verification API")
    @PostMapping("/verify/receipt")
    @ManageSession(sessionId = "#request.sid")
    @AnalyseTransaction(name = "receiptVerification")
    public ResponseEntity<?> verifyIap(@RequestBody IapVerificationRequest request) {
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().getCode());
        AnalyticService.update(request);
        BaseResponse<?> baseResponse = paymentManager.doVerifyIap(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString(),request);
        AnalyticService.update(baseResponse);
        return baseResponse.getResponse();
    }
}

package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.core.dto.request.IapVerificationRequest;
import in.wynk.payment.core.dto.response.BaseResponse;
import in.wynk.payment.core.enums.PaymentCode;
import in.wynk.payment.core.utils.BeanLocatorFactory;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.session.aspect.advice.ManageSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

@RestController
@RequestMapping("/wynk/v1/s2s")
public class RevenuePaymentsS2SHandler {

    @PostMapping("/verify/receipt/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "receiptVerification")
    public ResponseEntity<?> verifyIap(@PathVariable String sid, @RequestBody IapVerificationRequest request) {
        PaymentCode paymentCode = request.getPaymentCode();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        IMerchantIapPaymentVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantIapPaymentVerificationService.class);
        BaseResponse<?> baseResponse = verificationService.verifyReceipt(request);
        return baseResponse.getResponse();
    }
}

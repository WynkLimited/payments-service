package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.core.constant.ApplicationConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.payment.utils.BeanLocatorFactory;
import in.wynk.session.aspect.advice.ManageSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wynk/v1/s2s")
public class RevenuePaymentsS2SHandler {

    @PostMapping("/verify/receipt/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "receiptVerification")
    public ResponseEntity<?> verifyIap(@PathVariable String sid, @RequestBody IapVerificationRequest request) {
        PaymentCode paymentCode = request.getPaymentCode();
        AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, paymentCode.name());
        IMerchantIapPaymentVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantIapPaymentVerificationService.class);
        BaseResponse<?> baseResponse = verificationService.verifyReceipt(request);
        return baseResponse.getResponse();
    }
}

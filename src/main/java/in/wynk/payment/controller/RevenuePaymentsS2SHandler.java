package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IDummySessionGenerator;
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
@RequestMapping("/wynk/s2s")
public class RevenuePaymentsS2SHandler {

    private final PaymentManager paymentManager;
    private final IDummySessionGenerator dummySessionGenerator;

    public RevenuePaymentsS2SHandler(PaymentManager paymentManager, IDummySessionGenerator dummySessionGenerator) {
        this.paymentManager = paymentManager;
        this.dummySessionGenerator = dummySessionGenerator;
    }

    @PostMapping("/v1/payment/refund")
    @AnalyseTransaction(name = "initRefund")
    public ResponseEntity<?> doRefund(@RequestBody PaymentRefundInitRequest request) {
        AnalyticService.update(request);
        BaseResponse<?> baseResponse = paymentManager.initRefund(request);
        AnalyticService.update(baseResponse.getBody());
        return baseResponse.getResponse();
    }

    @ApiOperation("Accepts the receipt of various IAP partners." + "\nAn alternate API for old itunes/receipt and /amazon-iap/verification API")
    @PostMapping("/v1/verify/receipt")
    @ManageSession(sessionId = "#request.sid")
    @AnalyseTransaction(name = "receiptVerification")
    public ResponseEntity<?> verifyIap(@RequestBody IapVerificationRequest request) {
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().getCode());
        AnalyticService.update(request);
        BaseResponse<?> baseResponse = paymentManager.doVerifyIap(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString(), request);
        AnalyticService.update(baseResponse);
        return baseResponse.getResponse();
    }

    @ApiOperation("Accepts the receipt of various IAP partners." + "\nAn alternate API for old itunes/receipt and /amazon-iap/verification API")
    @PostMapping("/v2/verify/receipt")
    public ResponseEntity<?> verifyIap2(@RequestBody IapVerificationRequest request) {
        return verifyIap(dummySessionGenerator.initSession(request));
    }

}

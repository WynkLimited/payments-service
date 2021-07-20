package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.CustomerWindBackRequest;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.dto.S2SPurchaseDetails;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.ICustomerWinBackService;
import in.wynk.payment.service.IDummySessionGenerator;
import in.wynk.payment.service.PaymentManager;
import in.wynk.session.aspect.advice.ManageSession;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static in.wynk.common.constant.BaseConstants.ORIGINAL_SID;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s")
public class RevenuePaymentsS2SHandler {

    private final PaymentManager paymentManager;
    private final ICustomerWinBackService winBackService;
    private final IDummySessionGenerator dummySessionGenerator;

    @PostMapping("/v1/payment/charge")
    @AnalyseTransaction(name = "paymentCharging")
    public WynkResponseEntity<AbstractChargingResponse> doCharging(@RequestBody AbstractChargingRequest<S2SPurchaseDetails> request) {
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        AnalyticService.update(request);
        return paymentManager.charge(request);
    }

    @GetMapping("/v1/payment/status/{tid}")
    @AnalyseTransaction(name = "paymentStatus")
    public WynkResponseEntity<AbstractChargingStatusResponse> status(@PathVariable String tid) {
        return paymentManager.status(tid);
    }

    @PostMapping("/v1/payment/refund")
    @AnalyseTransaction(name = "initRefund")
    public WynkResponseEntity<AbstractPaymentRefundResponse> doRefund(@RequestBody PaymentRefundInitRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<AbstractPaymentRefundResponse> baseResponse = paymentManager.refund(request);
        AnalyticService.update(baseResponse.getBody());
        return baseResponse;
    }

    @GetMapping("/v1/customer/winback/{tid}")
    @AnalyseTransaction(name = "customerWinBack")
    public WynkResponseEntity<Void> winBack(@PathVariable String tid, @RequestParam Map<String, Object> params) {
        final CustomerWindBackRequest request = CustomerWindBackRequest.builder().dropoutTransactionId(tid).params(params).build();
        AnalyticService.update(request);
        final WynkResponseEntity<Void> response = winBackService.winBack(request);
        AnalyticService.update(response);
        return response;
    }

    @ApiOperation("Accepts the receipt of various IAP partners." + "\nAn alternate API for old itunes/receipt and /amazon-iap/verification API")
    @PostMapping("/v1/verify/receipt")
    @AnalyseTransaction(name = "receiptVerification")
    public ResponseEntity<?> verifyIap(@RequestBody IapVerificationRequest request) {
        AnalyticService.update(ORIGINAL_SID, request.getSid());
        return getResponseEntity(request);
    }

    @ApiOperation("Accepts the receipt of various IAP partners." + "\nAn alternate API for old itunes/receipt and /amazon-iap/verification API")
    @PostMapping("/v2/verify/receipt")
    @AnalyseTransaction(name = "receiptVerification")
    public ResponseEntity<?> verifyIap2(@RequestBody IapVerificationRequest request) {
        AnalyticService.update(ORIGINAL_SID, request.getSid());
        return getResponseEntity(dummySessionGenerator.initSession(request));
    }

    @ManageSession(sessionId = "#request.sid")
    private ResponseEntity<?> getResponseEntity(IapVerificationRequest request) {
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().getCode());
        AnalyticService.update(request);
        BaseResponse<?> baseResponse = paymentManager.doVerifyIap(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString(), request);
        AnalyticService.update(baseResponse);
        return baseResponse.getResponse();
    }

}
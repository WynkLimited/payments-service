package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.ORIGINAL_SID;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_CLIENT_AUTHORIZATION;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s")
public class RevenuePaymentS2SController {

    private final PaymentManager paymentManager;
    private final PaymentGatewayManager manager;
    private final ICustomerWinBackService winBackService;
    private final IQuickPayLinkGenerator quickPayLinkGenerator;
    private final IDummySessionGenerator dummySessionGenerator;

    @PostMapping("/v1/payment/charge")
    @AnalyseTransaction(name = "paymentCharging")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"PAYMENT_CHARGING_WRITE\")")
    public WynkResponseEntity<AbstractChargingResponse> doCharging(@Valid @RequestBody AbstractChargingRequest<S2SPurchaseDetails> request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        AnalyticService.update(request);
        return paymentManager.charge(request);
    }

    @GetMapping("/v1/payment/status/{tid}")
    @AnalyseTransaction(name = "paymentStatus")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"PAYMENT_STATUS_READ\")")
    public WynkResponseEntity<AbstractChargingStatusResponse> status(@PathVariable String tid) {
        LoadClientUtils.loadClient(true);
        return paymentManager.status(tid);
    }

    @SneakyThrows
    @GetMapping("/v2/payment/status/{tid}")
    @AnalyseTransaction(name = "paymentStatusV2")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"PAYMENT_STATUS_READ\")")
    public WynkResponse<TransactionDetailsDto> statusV2(@PathVariable String tid) {
        final TransactionSnapShot transactionSnapShot = paymentManager.statusV2(tid);
        final WynkResponse<TransactionDetailsDto> response = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPresentation<WynkResponse<TransactionDetailsDto>, TransactionSnapShot>>() {
        }).transform(transactionSnapShot);
        AnalyticService.update(response);
        return response;
    }

    @SneakyThrows
    @GetMapping("/v3/payment/status/{tid}")
    @AnalyseTransaction(name = "paymentStatusV3")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"PAYMENT_STATUS_READ\")")
    public WynkResponse<TransactionDetailsDtoV3> statusV3(@PathVariable String tid) {
        final TransactionSnapShot transactionSnapShot = paymentManager.statusV2(tid);
        final WynkResponse<TransactionDetailsDtoV3> response = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPresentation<WynkResponse<TransactionDetailsDtoV3>, TransactionSnapShot>>() {
        }).transform(transactionSnapShot);
        AnalyticService.update(response);
        return response;
    }

    @PostMapping("/v1/payment/refund")
    @AnalyseTransaction(name = "initRefund")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"INIT_REFUND_WRITE\")")
    public WynkResponseEntity<AbstractPaymentRefundResponse> doRefund(@Valid @RequestBody PaymentRefundInitRequest request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(request);
        WynkResponseEntity<AbstractPaymentRefundResponse> baseResponse = paymentManager.refund(request);
        AnalyticService.update(baseResponse.getBody());
        return baseResponse;
    }

    @GetMapping("/v1/customer/winback/{tid}")
    @AnalyseTransaction(name = "customerWinBack")
    public WynkResponseEntity<Void> winBack(@PathVariable String tid, @RequestParam Map<String, Object> params) {
        LoadClientUtils.loadClient(true);
        final CustomerWindBackRequest request = CustomerWindBackRequest.builder().dropoutTransactionId(tid).params(params).build();
        AnalyticService.update(request);
        final WynkResponseEntity<Void> response = winBackService.winBack(request);
        AnalyticService.update(response);
        return response;
    }

    @GetMapping("/v1/pay/link/{tid}")
    public WynkResponseEntity<String> quickPayLink(@PathVariable String tid) {
        LoadClientUtils.loadClient(true);
        return WynkResponseEntity.<String>builder().data(quickPayLinkGenerator.generate(tid)).build();
    }

    @PostMapping("/v1/verify/receipt")
    @AnalyseTransaction(name = "receiptVerification")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"RECEIPT_VERIFICATION_WRITE\")")
    @ApiOperation("Accepts the receipt of various IAP partners." + "\nAn alternate API for old itunes/receipt and /amazon-iap/verification API")
    public ResponseEntity<?> verifyIap(@Valid @RequestBody IapVerificationRequest request) {
        request.setOriginalSid();
        AnalyticService.update(ORIGINAL_SID, request.getSid());
        return getResponseEntity(request);
    }

    @PostMapping("/v2/verify/receipt")
    @AnalyseTransaction(name = "receiptVerification")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"RECEIPT_VERIFICATION_WRITE\")")
    @ApiOperation("Accepts the receipt of various IAP partners." + "\nAn alternate API for old itunes/receipt and /amazon-iap/verification API")
    public ResponseEntity<?> verifyIap2(@Valid @RequestBody IapVerificationRequest request) {
        request.setOriginalSid();
        AnalyticService.update(ORIGINAL_SID, request.getSid());
        try {
            SessionDTO session = loadSession(request.getSid());
            if(session.getSessionPayload().containsKey("successWebUrl") && session.getSessionPayload().get("successWebUrl") != null)
                request.setSuccessUrl(session.getSessionPayload().get("successWebUrl").toString());
            if(session.getSessionPayload().containsKey("failureWebUrl") && session.getSessionPayload().get("failureWebUrl") != null)
                request.setFailureUrl(session.getSessionPayload().get("failureWebUrl").toString()) ;
        } catch (Exception e) {
            //eat the exception if session id is not present in session, dummy sid will be generated in that case.
            //throw new WynkRuntimeException(e);
        }
        return getResponseEntity(dummySessionGenerator.initSession(request));
    }

    @PostMapping("/v3/verify/receipt")
    @AnalyseTransaction(name = "receiptVerification")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"RECEIPT_VERIFICATION_WRITE\")")
    @ApiOperation("Accepts the receipt of various IAP partners." + "\nAn alternate API for old itunes/receipt and /amazon-iap/verification API")
    public ResponseEntity<?> verifyIap(@Valid @RequestBody IapVerificationRequestV2 request) {
        request.setOriginalSid();
        AnalyticService.update(ORIGINAL_SID, request.getSessionDetails().getSessionId());
        return getResponseEntity(StringUtils.isNotBlank(request.getSessionDetails().getSessionId()) ? request : dummySessionGenerator.initSession(request));
    }

    @PostMapping("/v3/cancel/subscription/{uid}/{transactionId}")
    @AnalyseTransaction(name = "receiptVerification")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"RECEIPT_VERIFICATION_WRITE\")")
    @ApiOperation("Cancels the subscription for IAP")
    public void cancelSubscription (@PathVariable String uid, @PathVariable String transactionId) {
        manager.cancelSubscription(uid, transactionId);
    }

    @ManageSession(sessionId = "#request.sid")
    private ResponseEntity<?> getResponseEntity(IapVerificationRequest request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentGateway().getCode());
        AnalyticService.update(request);
        BaseResponse<?> baseResponse = paymentManager.doVerifyIap(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString(), request);
        AnalyticService.update(baseResponse);
        return baseResponse.getResponse();
    }

    @ManageSession(sessionId = "#sid")
    private SessionDTO loadSession(String sid) {
        return SessionContextHolder.getBody();
    }

    @ManageSession(sessionId = "#request.sessionDetails.sessionId")
    private ResponseEntity<?> getResponseEntity(IapVerificationRequestV2 request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().getCode());
        AnalyticService.update(request);
        BaseResponse<?> baseResponse = paymentManager.doVerifyIap(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString(),
                IapVerificationRequestV2Wrapper.builder().iapVerificationV2(request).latestReceiptResponse(null).build());
        AnalyticService.update(baseResponse);
        return baseResponse.getResponse();
    }

}
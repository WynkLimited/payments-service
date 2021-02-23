package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.PaymentManager;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST_PAYLOAD;

@RestController
@RequestMapping("/wynk/v1/payment")
public class RevenuePaymentHandler {

    private final PaymentManager paymentManager;
    private final Gson gson;

    public RevenuePaymentHandler(PaymentManager paymentManager, Gson gson) {
        this.paymentManager = paymentManager;
        this.gson = gson;
    }

    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public ResponseEntity<?> doCharging(@PathVariable String sid, @RequestBody ChargingRequest request) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String uid = sessionDTO.get(SessionKeys.UID);
        final String msisdn = Utils.getTenDigitMsisdn(sessionDTO.get(SessionKeys.MSISDN));
        if (request.getPlanId() == 0 && StringUtils.isBlank(request.getItemId())) {
            throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid planId or itemId");
        }
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        AnalyticService.update(request);
        BaseResponse<?> baseResponse = paymentManager.doCharging(uid, msisdn, request);
        return baseResponse.getResponse();
    }

    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public ResponseEntity<?> status(@PathVariable String sid) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(SessionKeys.PAYMENT_CODE));
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AbstractTransactionStatusRequest request = ChargingTransactionStatusRequest.builder().transactionId(sessionDTO.get(SessionKeys.TRANSACTION_ID)).paymentCode(paymentCode.getCode()).build();
        BaseResponse<?> baseResponse = paymentManager.status(request);
        return baseResponse.getResponse();
    }

    @PostMapping("/verify/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "verifyUserPaymentBin")
    public ResponseEntity<?> verify(@PathVariable String sid, @RequestBody VerificationRequest request) {
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        AnalyticService.update(request);
        BaseResponse<?> baseResponse = paymentManager.doVerify(request);
        return baseResponse.getResponse();
    }

    @PostMapping(path = "/callback/{sid}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handleCallback(@PathVariable String sid, @RequestParam Map<String, Object> payload) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String transactionId = sessionDTO.get(SessionKeys.TRANSACTION_ID);
        final PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(SessionKeys.PAYMENT_CODE));
        final CallbackRequest request = CallbackRequest.builder().body(payload).transactionId(transactionId).build();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        BaseResponse<?> baseResponse = paymentManager.handleCallback(request, paymentCode);
        return baseResponse.getResponse();
    }

    @GetMapping("/callback/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handleCallbackGet(@PathVariable String sid, @RequestParam MultiValueMap<String, String> payload) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(SessionKeys.PAYMENT_CODE));
        final String transactionId = sessionDTO.get(SessionKeys.TRANSACTION_ID);
        CallbackRequest request = CallbackRequest.builder().body(payload).transactionId(transactionId).build();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        BaseResponse<?> baseResponse = paymentManager.handleCallback(request, paymentCode);
        return baseResponse.getResponse();
    }

}

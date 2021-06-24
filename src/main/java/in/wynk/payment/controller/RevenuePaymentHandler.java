package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.PaymentManager;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static in.wynk.common.constant.BaseConstants.PAYMENT_CODE;
import static in.wynk.common.constant.BaseConstants.TRANSACTION_ID;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST_PAYLOAD;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v1/payment")
public class RevenuePaymentHandler {

    private final Gson gson;
    private final PaymentManager paymentManager;

    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public ResponseEntity<?> doCharging(@PathVariable String sid, @RequestBody AbstractChargingRequest<?> request) {
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        AnalyticService.update(request);
        return paymentManager.charge(request);
    }

    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public ResponseEntity<?> status(@PathVariable String sid) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        AbstractTransactionStatusRequest request = ChargingTransactionStatusRequest.builder().transactionId(sessionDTO.get(TRANSACTION_ID)).build();
        return paymentManager.status(request);
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
        final String transactionId = sessionDTO.get(TRANSACTION_ID);
        final PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(PAYMENT_CODE));
        final CallbackRequestWrapper request = CallbackRequestWrapper.builder().body(payload).transactionId(transactionId).paymentCode(paymentCode).build();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        return paymentManager.handleCallback(request);
    }

    @GetMapping("/callback/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<?> handleCallbackGet(@PathVariable String sid, @RequestParam MultiValueMap<String, String> payload) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(PAYMENT_CODE));
        final String transactionId = sessionDTO.get(TRANSACTION_ID);
        CallbackRequestWrapper request = CallbackRequestWrapper.builder().body(payload).transactionId(transactionId).paymentCode(paymentCode).build();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        return paymentManager.handleCallback(request);
    }

    @PostMapping(path = "/callback/{sid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handleCallbackJSON(@PathVariable String sid, @RequestBody Map<String, Object> payload) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String transactionId = sessionDTO.get(TRANSACTION_ID);
        final PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(PAYMENT_CODE));
        final CallbackRequestWrapper request = CallbackRequestWrapper.builder().body(payload).transactionId(transactionId).paymentCode(paymentCode).build();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        return paymentManager.handleCallback(request);
    }

}

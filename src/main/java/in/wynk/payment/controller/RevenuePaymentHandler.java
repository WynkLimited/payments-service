package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.WebPurchaseDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.PaymentManager;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.*;
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
    public WynkResponseEntity<AbstractChargingResponse> doCharging(@PathVariable String sid, @RequestBody AbstractChargingRequest<WebPurchaseDetails> request) {
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        AnalyticService.update(request);
        return paymentManager.charge(request);
    }

    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public WynkResponseEntity<AbstractChargingStatusResponse> status(@PathVariable String sid) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        return paymentManager.status(sessionDTO.<String>get(TRANSACTION_ID));
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
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback(@PathVariable String sid, @RequestParam Map<String, Object> payload) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String transactionId = sessionDTO.get(TRANSACTION_ID);
        payload.put(TRANSACTION_ID_FULL, transactionId);
        final PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(PAYMENT_CODE));
        final CallbackRequestWrapper<?> request = CallbackRequestWrapper.builder().paymentCode(paymentCode).payload(payload).build();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        return paymentManager.handleCallback(request);
    }

    @GetMapping("/callback/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<AbstractCallbackResponse> handleCallbackGet(@PathVariable String sid, @RequestParam MultiValueMap<String, String> payload) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String transactionId = sessionDTO.get(TRANSACTION_ID);
        final Map<String, Object> terraformed = new HashMap<>(payload.toSingleValueMap());
        terraformed.put(TRANSACTION_ID_FULL, transactionId);
        final PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(PAYMENT_CODE));
        final CallbackRequestWrapper<?> request = CallbackRequestWrapper.builder().paymentCode(paymentCode).payload(terraformed).build();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        return paymentManager.handleCallback(request);
    }

    @PostMapping(path = "/callback/{sid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<AbstractCallbackResponse> handleCallbackJSON(@PathVariable String sid, @RequestBody Map<String, Object> payload) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String transactionId = sessionDTO.get(TRANSACTION_ID);
        payload.put(TRANSACTION_ID_FULL, transactionId);
        final PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(PAYMENT_CODE));
        final CallbackRequestWrapper<?> request = CallbackRequestWrapper.builder().paymentCode(paymentCode).payload(payload).build();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        return paymentManager.handleCallback(request);
    }

}
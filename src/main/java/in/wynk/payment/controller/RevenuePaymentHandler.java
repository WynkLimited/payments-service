package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.payment.core.constant.ApplicationConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.enums.StatusMode;
import in.wynk.payment.scheduler.PaymentRenewalsScheduler;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.service.IMerchantPaymentStatusService;
import in.wynk.payment.service.IMerchantVerificationService;
import in.wynk.payment.utils.BeanLocatorFactory;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/wynk/v1/payment")
public class RevenuePaymentHandler {

    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public ResponseEntity<?> doCharging(@PathVariable String sid, @RequestBody ChargingRequest request) {
        PaymentCode paymentCode = request.getPaymentCode();
        AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, paymentCode.name());
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        BaseResponse<?> baseResponse = chargingService.doCharging(request);
        return baseResponse.getResponse();
    }

    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public ResponseEntity<?> status(@PathVariable String sid) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        ChargingStatusRequest request = ChargingStatusRequest.builder().mode(StatusMode.LOCAL).transactionId(sessionDTO.get(SessionKeys.WYNK_TRANSACTION_ID)).build();
        PaymentCode paymentCode = sessionDTO.get(SessionKeys.PAYMENT_CODE);
        AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, paymentCode.name());
        IMerchantPaymentStatusService statusService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentStatusService.class);
        BaseResponse<?> baseResponse = statusService.status(request);
        return baseResponse.getResponse();
    }

    @PostMapping("/verify")
    @AnalyseTransaction(name = "verify")
    public ResponseEntity<?> verify(@RequestBody VerificationRequest request) {
        IMerchantVerificationService verificationService;
        PaymentCode paymentCode = request.getPaymentCode();
        AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, paymentCode.name());
        verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantVerificationService.class);
        BaseResponse<?> baseResponse = verificationService.doVerify(request);
        return baseResponse.getResponse();
    }

    @PostMapping(value = "/callback/{sid}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handleCallback(@PathVariable String sid, @RequestParam Map<String, Object> payload) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        CallbackRequest<Map<String, Object>> request = CallbackRequest.<Map<String, Object>>builder().body(payload).build();
        PaymentCode paymentCode = sessionDTO.get(SessionKeys.PAYMENT_CODE);
        AnalyticService.update(SessionKeys.PAYMENT_CODE, paymentCode.name());
        AnalyticService.update(ApplicationConstant.REQUEST_PAYLOAD, payload.toString());
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.name(), IMerchantPaymentCallbackService.class);
        BaseResponse<?> baseResponse = callbackService.handleCallback(request);
        return baseResponse.getResponse();
    }

    @GetMapping("/callback/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handleCallbackGet(@PathVariable String sid, @RequestParam MultiValueMap<String, String> payload) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        CallbackRequest<MultiValueMap<String, String>> request = CallbackRequest.<MultiValueMap<String, String>>builder().body(payload).build();
        PaymentCode paymentCode = sessionDTO.get(SessionKeys.PAYMENT_CODE);
        AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(ApplicationConstant.REQUEST_PAYLOAD, payload.toString());
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentCallbackService.class);
        BaseResponse<?> baseResponse = callbackService.handleCallback(request);
        return baseResponse.getResponse();
    }

}

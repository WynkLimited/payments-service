package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.ApplicationConstant;
import in.wynk.payment.constant.PaymentErrorType;
import in.wynk.payment.constant.PaymentOption;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.service.IMerchantPaymentStatusService;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payment")
public class RevenuePaymentHandler {

    private final ApplicationContext context;

    public RevenuePaymentHandler(ApplicationContext context) {
        this.context = context;
    }

    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public ResponseEntity<?> doCharging(@PathVariable String sid, @RequestBody ChargingRequest request) {
        IMerchantPaymentChargingService chargingService;
        try {
            AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, request.getPaymentOption().name());
            chargingService = this.context.getBean(request.getPaymentOption().getType(), IMerchantPaymentChargingService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY001);
        }
        BaseResponse<?> baseResponse = chargingService.doCharging(request);
        return baseResponse.getResponse();
    }

    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public ResponseEntity<?> status(@PathVariable String sid) {
        IMerchantPaymentStatusService statusService;
        Session<Map<String, Object>> session = SessionContextHolder.get();
        ChargingStatusRequest request = ChargingStatusRequest.builder().sessionId(session.getId().toString()).build();
        try {
            PaymentOption paymentOption = (PaymentOption) session.getBody().get(ApplicationConstant.PAYMENT_METHOD);
            AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, paymentOption.name());
            statusService = this.context.getBean(paymentOption.getType(), IMerchantPaymentStatusService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY001);
        }
        BaseResponse<?> baseResponse = statusService.status(request);
        return baseResponse.getResponse();
    }

    @PostMapping("/callback/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handleCallback(@PathVariable String sid, @RequestBody Map<String, Object> payload) {
        IMerchantPaymentCallbackService callbackService;
        Session<Map<String, Object>> session = SessionContextHolder.get();
        CallbackRequest<Map<String, Object>> request = CallbackRequest.<Map<String, Object>>builder().body(payload).build();
        try {
            PaymentOption option = ((PaymentOption) session.getBody().get(ApplicationConstant.PAYMENT_METHOD));
            AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, option.name());
            AnalyticService.update(ApplicationConstant.REQUEST_PAYLOAD, payload.toString());
            callbackService = this.context.getBean(option.getType(), IMerchantPaymentCallbackService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY001);
        }
        BaseResponse<?> baseResponse = callbackService.handleCallback(request);
        return baseResponse.getResponse();
    }

}

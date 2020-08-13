package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.utils.Utils;
import in.wynk.payment.TransactionContext;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.StatusMode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST_PAYLOAD;

@RestController
@RequestMapping("/wynk/v1/payment")
public class RevenuePaymentHandler {

    @Autowired
    private PaymentManager paymentManager;

    @Autowired
    private ITransactionManagerService transactionManager;

    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public ResponseEntity<?> doCharging(@PathVariable String sid, @RequestBody ChargingRequest request) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String uid = sessionDTO.get(SessionKeys.UID);
        final String msisdn = Utils.getTenDigitMsisdn(sessionDTO.get(SessionKeys.MSISDN));
        BaseResponse<?> baseResponse =  paymentManager.doCharging(request, uid, msisdn);
        return baseResponse.getResponse();
    }

    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public ResponseEntity<?> status(@PathVariable String sid) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        ChargingStatusRequest request = ChargingStatusRequest.builder().mode(StatusMode.LOCAL).transactionId(sessionDTO.get(SessionKeys.WYNK_TRANSACTION_ID)).build();
        PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(SessionKeys.PAYMENT_CODE));
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        BaseResponse<?> baseResponse = paymentManager.status(request, paymentCode);
        return baseResponse.getResponse();
    }

    //TODO: add SID
    @PostMapping("/verify")
    @AnalyseTransaction(name = "verifyUserPaymentBin")
    public ResponseEntity<?> verify(@RequestBody VerificationRequest request) {
        BaseResponse<?> baseResponse = paymentManager.doVerify(request);
        return baseResponse.getResponse();
    }

    @PostMapping(path = "/callback/{sid}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handleCallback(@PathVariable String sid, @RequestParam Map<String, Object> payload) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        CallbackRequest request = CallbackRequest.builder().body(payload).build();
        final String transactionId = sessionDTO.get(SessionKeys.WYNK_TRANSACTION_ID).toString();
        final Transaction transaction = transactionManager.get(transactionId);
        TransactionContext.set(transaction);
        PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(SessionKeys.PAYMENT_CODE));
        BaseResponse<?> baseResponse = paymentManager.handleCallback(request, paymentCode);
        return baseResponse.getResponse();
    }

    @GetMapping("/callback/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handleCallbackGet(@PathVariable String sid, @RequestParam MultiValueMap<String, String> payload) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        CallbackRequest request = CallbackRequest.builder().body(payload).build();
        PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(SessionKeys.PAYMENT_CODE));
        final String transactionId = sessionDTO.get(SessionKeys.WYNK_TRANSACTION_ID).toString();
        final Transaction transaction = transactionManager.get(transactionId);
        TransactionContext.set(transaction);
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        AnalyticService.update(REQUEST_PAYLOAD, payload.toString());
        BaseResponse<?> baseResponse = paymentManager.handleCallback(request, paymentCode);
        return baseResponse.getResponse();
    }

}

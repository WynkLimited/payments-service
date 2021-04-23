package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.paytm.WalletAddMoneyRequest;
import in.wynk.payment.dto.paytm.WalletLinkRequest;
import in.wynk.payment.dto.paytm.WalletValidateLinkRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantWalletService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static in.wynk.common.constant.BaseConstants.PLAN_ID;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

@RestController
@RequestMapping("/wynk/v1/wallet")
public class RevenuePaymentWalletHandler {

    private final ApplicationContext context;
    private final PaymentManager paymentManager;

    public RevenuePaymentWalletHandler(ApplicationContext context, PaymentManager paymentManager) {
        this.context = context;
        this.paymentManager = paymentManager;
    }

    @PostMapping("/link/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletLink")
    public ResponseEntity<?> linkRequest(@PathVariable String sid, @RequestBody WalletLinkRequest request) {
        IMerchantWalletService walletService;
        try {
            AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
            walletService = this.context.getBean(request.getPaymentCode().getCode(), IMerchantWalletService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
        BaseResponse<?> baseResponse = walletService.linkRequest(request);
        return baseResponse.getResponse();
    }

    @PostMapping("/link/validate/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletValidateLink")
    public ResponseEntity<?> linkValidate(@PathVariable String sid, @RequestBody WalletValidateLinkRequest request) {
        IMerchantWalletService walletService;
        try {
            AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
            walletService = this.context.getBean(request.getPaymentCode().getCode(), IMerchantWalletService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
        BaseResponse<?> baseResponse = walletService.validateLink(request);
        return baseResponse.getResponse();
    }

    @GetMapping("/unlink/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletUnlink")
    public ResponseEntity<?> unlink(@PathVariable String sid, @RequestParam  String paymentCode) {
        IMerchantWalletService walletService;
        try {
            AnalyticService.update(PAYMENT_METHOD, paymentCode);
            walletService = this.context.getBean(PaymentCode.valueOf(paymentCode).getCode(), IMerchantWalletService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
        BaseResponse<?> baseResponse = walletService.unlink();
        return baseResponse.getResponse();
    }

    @GetMapping("/balance/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletBalance")
    public ResponseEntity<?> balance(@PathVariable String sid, @RequestParam  String paymentCode) {
        IMerchantWalletService walletService;
        try {
            AnalyticService.update(PAYMENT_METHOD, paymentCode);
            walletService = this.context.getBean(PaymentCode.valueOf(paymentCode).getCode(), IMerchantWalletService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
       BaseResponse<?> baseResponse = walletService.balance();
        return baseResponse.getResponse();
    }

    @PostMapping("/addMoney/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletAddMoney")
    public ResponseEntity<?> addMoney(@PathVariable String sid, @RequestBody WalletAddMoneyRequest request) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String uid = sessionDTO.get(SessionKeys.UID);
        final String msisdn = Utils.getTenDigitMsisdn(sessionDTO.get(SessionKeys.MSISDN));
        if (request.getPlanId() == 0 && StringUtils.isBlank(request.getItemId())) {
            throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid planId or itemId");
        }
        sessionDTO.put(PLAN_ID, request.getPlanId());
        sessionDTO.put(SessionKeys.PAYMENT_CODE,request.getPaymentCode().getCode());
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().getCode());
        AnalyticService.update(request);
        BaseResponse<?> baseResponse = paymentManager.addMoney(uid, msisdn, request);
        return baseResponse.getResponse();
    }
}

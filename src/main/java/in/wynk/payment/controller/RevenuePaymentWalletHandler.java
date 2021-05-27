package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.request.WalletAddMoneyRequest;
import in.wynk.payment.dto.request.WalletLinkRequest;
import in.wynk.payment.dto.request.WalletValidateLinkRequest;
import in.wynk.payment.service.IMerchantWalletService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

@RestController
@RequestMapping("/wynk/v1/wallet")
public class RevenuePaymentWalletHandler {

    private final PaymentManager paymentManager;

    public RevenuePaymentWalletHandler(PaymentManager paymentManager) {
        this.paymentManager = paymentManager;
    }

    private IMerchantWalletService getMerchantWalletService(PaymentCode paymentCode) {
        try {
            AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
            return BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantWalletService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
    }

    @PostMapping("/link/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletLink")
    public ResponseEntity<?> linkRequest(@PathVariable String sid, @RequestBody WalletLinkRequest request) {
        return getMerchantWalletService(request.getPaymentCode()).linkRequest(request).getResponse();
    }

    @PostMapping("/link/validate/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletValidateLink")
    public ResponseEntity<?> linkValidate(@PathVariable String sid, @RequestBody WalletValidateLinkRequest request) {
        return getMerchantWalletService(request.getPaymentCode()).validateLink(request).getResponse();
    }

    @GetMapping("/unlink/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletUnlink")
    public ResponseEntity<?> unlink(@PathVariable String sid, @RequestParam PaymentCode paymentCode) {
        return getMerchantWalletService(paymentCode).unlink().getResponse();
    }

    @GetMapping("/balance/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletBalance")
    public ResponseEntity<?> balance(@PathVariable String sid, @RequestParam int planId, @RequestParam PaymentCode paymentCode) {
        return getMerchantWalletService(paymentCode).balance(planId).getResponse();
    }

    @PostMapping("/addMoney/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletAddMoney")
    public ResponseEntity<?> addMoney(@PathVariable String sid, @RequestBody WalletAddMoneyRequest request) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String uid = sessionDTO.get(UID);
        final String msisdn = Utils.getTenDigitMsisdn(sessionDTO.get(MSISDN));
        if (request.getPlanId() == 0 && StringUtils.isBlank(request.getItemId())) {
            throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid planId or itemId");
        }
        sessionDTO.put(PLAN_ID, request.getPlanId());
        sessionDTO.put(PAYMENT_CODE, request.getPaymentCode().getCode());
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        AnalyticService.update(request);
        return paymentManager.addMoney(uid, msisdn, request).getResponse();
    }

}
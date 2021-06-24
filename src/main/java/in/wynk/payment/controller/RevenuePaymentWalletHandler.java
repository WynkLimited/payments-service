package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
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
            return BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantWalletService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
    }

    @PostMapping("/link/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletLink")
    public ResponseEntity<?> linkRequest(@PathVariable String sid, @RequestBody WalletLinkRequest request) {
        AnalyticService.update(request);
        BaseResponse<?> response = getMerchantWalletService(request.getPaymentCode()).linkRequest(request);
        AnalyticService.update(response.getBody());
        return response.getResponse();
    }

    @PostMapping("/link/validate/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletValidateLink")
    public ResponseEntity<?> linkValidate(@PathVariable String sid, @RequestBody WalletValidateLinkRequest request) {
        AnalyticService.update(request);
        BaseResponse<?> response = getMerchantWalletService(request.getPaymentCode()).validateLink(request);
        AnalyticService.update(response.getBody());
        return response.getResponse();
    }

    @PostMapping("/unlink/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletUnlink")
    public ResponseEntity<?> unlink(@PathVariable String sid, @RequestBody WalletRequest request) {
        AnalyticService.update(request);
        BaseResponse<?> response = getMerchantWalletService(request.getPaymentCode()).unlink();
        AnalyticService.update(response.getBody());
        return response.getResponse();
    }

    @PostMapping("/balance/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletBalance")
    public ResponseEntity<?> balance(@PathVariable String sid, @RequestBody WalletBalanceRequest request) {
        AnalyticService.update(request);
        BaseResponse<?> response = getMerchantWalletService(request.getPaymentCode()).balance(request.getPlanId());
        AnalyticService.update(response.getBody());
        return response.getResponse();
    }

    @PostMapping("/addMoney/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletAddMoney")
    public ResponseEntity<?> addMoney(@PathVariable String sid, @RequestBody WalletAddMoneyRequest<AbstractChargingRequest.IWebChargingDetails> request) {
        AnalyticService.update(request);
        BaseResponse<?> response = paymentManager.addMoney(request);
        AnalyticService.update(response.getBody());
        return response.getResponse();
    }

}
package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.request.WalletRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantWalletService;
import in.wynk.session.aspect.advice.ManageSession;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

@RestController
@RequestMapping("/wynk/v1/wallet")
public class RevenuePaymentWalletHandler {

    private final ApplicationContext context;

    public RevenuePaymentWalletHandler(ApplicationContext context) {
        this.context = context;
    }

    @PostMapping("/link/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletLink")
    public ResponseEntity<?> linkRequest(@PathVariable String sid, @RequestBody WalletRequest request) {
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
    public ResponseEntity<?> linkValidate(@PathVariable String sid, @RequestBody WalletRequest request) {
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


    @PostMapping("/unlink/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletUnlink")
    public ResponseEntity<?> unlink(@PathVariable String sid, @RequestBody WalletRequest request) {
        IMerchantWalletService walletService;
        try {
            AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
            walletService = this.context.getBean(request.getPaymentCode().getCode(), IMerchantWalletService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
        BaseResponse<?> baseResponse = walletService.unlink(request);
        return baseResponse.getResponse();
    }

    @GetMapping("/balance/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletBalance")
    public ResponseEntity<?> balance(@PathVariable String sid, @RequestParam PaymentCode paymentCode) {
        IMerchantWalletService walletService;
        try {
            AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
            walletService = this.context.getBean(paymentCode.getCode(), IMerchantWalletService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
        BaseResponse<?> baseResponse = walletService.balance();
        return baseResponse.getResponse();
    }

    @PostMapping("/addMoney/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletAddMoney")
    public ResponseEntity<?> addMoney(@PathVariable String sid, @RequestBody WalletRequest request) {
        IMerchantWalletService walletService;
        try {
            AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
            walletService = this.context.getBean(request.getPaymentCode().getCode(), IMerchantWalletService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
        BaseResponse<?> baseResponse = walletService.addMoney(request);
        return baseResponse.getResponse();
    }

}

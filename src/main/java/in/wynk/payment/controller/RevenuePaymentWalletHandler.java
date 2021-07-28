package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.WebPurchaseDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.UserWalletDetails;
import in.wynk.payment.service.*;
import in.wynk.session.aspect.advice.ManageSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v1/wallet")
public class RevenuePaymentWalletHandler {

    private final PaymentManager paymentManager;

    @PostMapping("/link/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletLink")
    public ResponseEntity<?> linkRequest(@PathVariable String sid, @RequestBody WebWalletLinkRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletLinkService<Void, WalletLinkRequest>>() {
        }).link(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/link/validate/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletValidateLink")
    public ResponseEntity<?> linkValidate(@PathVariable String sid, @RequestBody WebWalletValidateLinkRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletValidateLinkService<Void, WalletValidateLinkRequest>>() {
        }).validate(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/unlink/request/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletUnlink")
    public ResponseEntity<?> unlink(@PathVariable String sid, @RequestBody WebWalletDeLinkRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletDeLinkService<Void, WalletDeLinkRequest>>() {
        }).deLink(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/balance/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletBalance")
    public ResponseEntity<?> balance(@PathVariable String sid, @RequestBody WebWalletBalanceRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletBalanceService<UserWalletDetails, WalletBalanceRequest>>() {
        }).balance(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/addMoney/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletAddMoney")
    public ResponseEntity<?> addMoney(@PathVariable String sid, @RequestBody WalletTopUpRequest<WebPurchaseDetails> request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = paymentManager.topUp(request);
        AnalyticService.update(response.getBody());
        return response;
    }

}
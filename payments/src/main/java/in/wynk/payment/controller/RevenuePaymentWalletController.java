package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.WebPurchaseDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.UserWalletDetails;
import in.wynk.payment.dto.response.WalletLinkResponse;
import in.wynk.payment.dto.response.WalletTopUpResponse;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v1/wallet")
public class RevenuePaymentWalletController {

    private final PaymentManager paymentManager;

    @ManageSession(sessionId = "#sid")
    @PostMapping("/link/request/{sid}")
    @AnalyseTransaction(name = "walletLink")
    public WynkResponseEntity<WalletLinkResponse> linkRequest(@PathVariable String sid, @Valid @RequestBody WebWalletLinkRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        WynkResponseEntity<WalletLinkResponse> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletLinkService<WalletLinkResponse, WalletLinkRequest>>() {
        }).link(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @ManageSession(sessionId = "#sid")
    @PostMapping("/link/validate/{sid}")
    @AnalyseTransaction(name = "walletValidateLink")
    public WynkResponseEntity<Void> linkValidate(@PathVariable String sid, @Valid @RequestBody WebWalletValidateLinkRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        WynkResponseEntity<Void> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletValidateLinkService<Void, WalletValidateLinkRequest>>() {
        }).validate(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @ManageSession(sessionId = "#sid")
    @PostMapping("/unlink/request/{sid}")
    @AnalyseTransaction(name = "walletUnlink")
    public WynkResponseEntity<Void> unlink(@PathVariable String sid, @Valid @RequestBody WebWalletDeLinkRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        WynkResponseEntity<Void> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletDeLinkService<Void, WalletDeLinkRequest>>() {
        }).deLink(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/balance/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletBalance")
    public WynkResponseEntity<UserWalletDetails> balance(@PathVariable String sid, @Valid @RequestBody WebWalletBalanceRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        WynkResponseEntity<UserWalletDetails> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletBalanceService<UserWalletDetails, WalletBalanceRequest>>() {
        }).balance(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/addMoney/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "walletAddMoney")
    public WynkResponseEntity<WalletTopUpResponse> addMoney(@PathVariable String sid, @Valid @RequestBody WalletTopUpRequest<WebPurchaseDetails> request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        WynkResponseEntity<WalletTopUpResponse> response = paymentManager.topUp(request);
        AnalyticService.update(response.getBody());
        return response;
    }

}
package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.S2SPurchaseDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.UserWalletDetails;
import in.wynk.payment.dto.response.WalletLinkResponse;
import in.wynk.payment.dto.response.WalletTopUpResponse;
import in.wynk.payment.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s/wallet")
public class RevenuePaymentWalletS2SController {

    private final PaymentManager paymentManager;

    @PostMapping("/v1/link/request")
    @AnalyseTransaction(name = "walletLink")
    public WynkResponseEntity<WalletLinkResponse> linkRequest(@Valid @RequestBody S2SWalletLinkRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<WalletLinkResponse> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletLinkService<WalletLinkResponse, WalletLinkRequest>>() {
        }).link(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/v1/link/validate")
    @AnalyseTransaction(name = "walletValidateLink")
    public WynkResponseEntity<Void> linkValidate(@Valid @RequestBody S2SWalletValidateLinkRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<Void> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletValidateLinkService<Void, WalletValidateLinkRequest>>() {
        }).validate(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/v1/unlink/request")
    @AnalyseTransaction(name = "walletUnlink")
    public WynkResponseEntity<Void> unlink(@Valid @RequestBody S2SWalletDeLinkRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<Void> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletDeLinkService<Void, WalletDeLinkRequest>>() {
        }).deLink(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/v1/balance")
    @AnalyseTransaction(name = "walletBalance")
    public WynkResponseEntity<UserWalletDetails> balance(@Valid @RequestBody S2SWalletBalanceRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<UserWalletDetails> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletBalanceService<UserWalletDetails, WalletBalanceRequest>>() {
        }).balance(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/v1/addMoney")
    @AnalyseTransaction(name = "walletAddMoney")
    public WynkResponseEntity<WalletTopUpResponse> addMoney(@Valid @RequestBody WalletTopUpRequest<S2SPurchaseDetails> request) {
        AnalyticService.update(request);
        WynkResponseEntity<WalletTopUpResponse> response = paymentManager.topUp(request);
        AnalyticService.update(response.getBody());
        return response;
    }

}
package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.S2SPurchaseDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.UserWalletDetails;
import in.wynk.payment.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s/wallet")
public class RevenuePaymentS2SWalletHandler {

    private final PaymentManager paymentManager;

    @PostMapping("/v1/link/request")
    @AnalyseTransaction(name = "walletLink")
    public ResponseEntity<?> linkRequest(@RequestBody WalletLinkRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletLinkService<Void, WalletLinkRequest>>() {
        }).link(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/v1/link/validate")
    @AnalyseTransaction(name = "walletValidateLink")
    public ResponseEntity<?> linkValidate(@RequestBody WalletValidateLinkRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletValidateLinkService<Void, WalletValidateLinkRequest>>() {
        }).validate(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/v1/unlink/request")
    @AnalyseTransaction(name = "walletUnlink")
    public ResponseEntity<?> unlink(@RequestBody WalletDeLinkRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletDeLinkService<Void, WalletDeLinkRequest>>() {
        }).deLink(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/v1/balance")
    @AnalyseTransaction(name = "walletBalance")
    public ResponseEntity<?> balance(@RequestBody WalletBalanceRequest request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IWalletBalanceService<UserWalletDetails, WalletBalanceRequest>>() {
        }).balance(request);
        AnalyticService.update(response.getBody());
        return response;
    }

    @PostMapping("/v1/addMoney")
    @AnalyseTransaction(name = "walletAddMoney")
    public ResponseEntity<?> addMoney(@RequestBody WalletTopUpRequest<S2SPurchaseDetails> request) {
        AnalyticService.update(request);
        WynkResponseEntity<?> response = paymentManager.topUp(request);
        AnalyticService.update(response.getBody());
        return response;
    }

}
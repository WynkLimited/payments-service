package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.S2SPaymentOptionsRequest;
import in.wynk.payment.dto.request.AbstractPreferredPaymentDetailsControllerRequest;
import in.wynk.payment.dto.request.CombinedS2SPaymentDetailsRequest;
import in.wynk.payment.dto.request.DefaultPaymentOptionRequest;
import in.wynk.payment.dto.response.CombinedPaymentDetailsResponse;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.gateway.aps.common.ApsCommonGateway;
import in.wynk.payment.gateway.aps.pay.options.ApsPaymentOptionsGateway;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.IUserPreferredPaymentService;
import in.wynk.payment.utils.LoadClientUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s/v1/payment")
public class PaymentOptionsS2SController {

    private final IPaymentOptionService paymentMethodService;
    private final ApsPaymentOptionsGateway gateway;
    private final ApsCommonGateway common;
    private final IUserPreferredPaymentService<CombinedPaymentDetailsResponse, AbstractPreferredPaymentDetailsControllerRequest<?>> preferredPaymentService;

    @PostMapping("/options")
    @AnalyseTransaction(name = "paymentOptions")
    public WynkResponseEntity<PaymentOptionsDTO> getFilteredPaymentMethods(@RequestBody DefaultPaymentOptionRequest<S2SPaymentOptionsRequest> request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(request);
        return paymentMethodService.getFilteredPaymentOptions(request);
    }

    @PostMapping("/saved/details")
    @AnalyseTransaction(name = "savedDetails")
    public WynkResponseEntity<CombinedPaymentDetailsResponse> getPaymentDetails(@RequestBody CombinedS2SPaymentDetailsRequest request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(request);
        WynkResponseEntity<CombinedPaymentDetailsResponse> detailsResponse = preferredPaymentService.getUserPreferredPayments(request);
        AnalyticService.update(detailsResponse.getBody());
        return detailsResponse;
    }

}
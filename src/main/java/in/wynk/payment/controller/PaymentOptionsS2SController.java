package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.S2SPaymentOptionsRequest;
import in.wynk.payment.dto.aps.response.option.ApsPaymentOptionsResponse;
import in.wynk.payment.dto.request.AbstractPreferredPaymentDetailsControllerRequest;
import in.wynk.payment.dto.request.CombinedS2SPaymentDetailsRequest;
import in.wynk.payment.dto.request.DefaultPaymentOptionRequest;
import in.wynk.payment.dto.response.CombinedPaymentDetailsResponse;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.gateway.aps.service.ApsCommonGatewayService;
import in.wynk.payment.gateway.aps.service.ApsPaymentOptionsGatewayService;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.IUserPreferredPaymentService;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s/v1/payment")
public class PaymentOptionsS2SController {

    private final IPaymentOptionService paymentMethodService;
    private final ApsPaymentOptionsGatewayService gateway;
    private final ApsCommonGatewayService common;
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

    @PostMapping("/option/aps/{sid}")
    @ManageSession(sessionId = "#sid")
    public WynkResponseEntity<ApsPaymentOptionsResponse> option(@PathVariable String sid, @Valid String request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        SessionDTO sessionDTO= SessionContextHolder.getBody();
        ApsPaymentOptionsResponse res = gateway.payOption(common.getLoginId(sessionDTO.get("msisdn")));
        return  WynkResponseEntity.<ApsPaymentOptionsResponse>builder().data(res).status(HttpStatus.OK).build();
    }

}
package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.IErrorDetails;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.aps.common.HealthStatus;
import in.wynk.payment.dto.aps.response.option.ApsPaymentOptionsResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.request.VerificationRequestV2;
import in.wynk.payment.gateway.aps.paymentOptions.ApsPaymentOptionsGateway;
import in.wynk.payment.presentation.dto.verify.VerifyUserPaymentResponse;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v3/payment")
public class RevenuePaymentControllerV3 {

    private final PaymentGatewayManager paymentGatewayManager;
    private final ApsPaymentOptionsGateway gateway;

    //This version is for payment refactoring task
    @PostMapping("/verify/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "verifyUserPaymentBin")
    public WynkResponseEntity<VerifyUserPaymentResponse> verifyV2(@PathVariable String sid, @Valid @RequestBody VerificationRequestV2 request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        final WynkResponseEntity<VerifyUserPaymentResponse> responseEntity = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IWynkPresentation<WynkResponseEntity<VerifyUserPaymentResponse>, AbstractVerificationResponse>>() {
        }).transform(paymentGatewayManager.verify(request));
        AnalyticService.update(responseEntity.getBody().getData());
        return responseEntity;
    }

    @PostMapping("/option/{sid}")
    @ManageSession(sessionId = "#sid")
    public WynkResponseEntity<ApsPaymentOptionsResponse> option(@PathVariable String sid, @Valid @RequestBody VerificationRequestV2 request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        ApsPaymentOptionsResponse res = gateway.payOption();
        return /*new WynkResponseEntity<ApsPaymentOptionsResponse>(res, HttpStatus.OK, new HttpHeaders());*/null;
    }
}

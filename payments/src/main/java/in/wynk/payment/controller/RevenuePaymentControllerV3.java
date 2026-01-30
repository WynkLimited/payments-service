package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.request.AbstractVerificationRequest;
import in.wynk.payment.dto.request.WebVerificationRequest;
import in.wynk.payment.presentation.dto.verify.VerifyUserPaymentResponse;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v3/payment")
public class RevenuePaymentControllerV3 {

    private final PaymentGatewayManager paymentGatewayManager;

    @SneakyThrows
    @PostMapping("/verify/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "verifyUserPaymentBin")
    public WynkResponseEntity<VerifyUserPaymentResponse> verify (@PathVariable String sid, @Valid @RequestBody WebVerificationRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        final WynkResponseEntity<VerifyUserPaymentResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IWynkPresentation<WynkResponseEntity<VerifyUserPaymentResponse>, AbstractVerificationResponse>>() {
                }).transform(paymentGatewayManager.verify(request));
        AnalyticService.update(responseEntity.getBody().getData());
        return responseEntity;
    }
}

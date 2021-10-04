package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.dto.S2SPaymentOptionsRequest;
import in.wynk.payment.dto.request.DefaultPaymentOptionRequest;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionService;
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

    @PostMapping("/options")
    @AnalyseTransaction(name = "paymentOptions")
    public PaymentOptionsDTO getFilteredPaymentMethods(@RequestBody DefaultPaymentOptionRequest<S2SPaymentOptionsRequest> request) {
        AnalyticService.update(request);
        return paymentMethodService.getFilteredPaymentOptions(request);
    }
}

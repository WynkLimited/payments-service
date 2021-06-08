package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.dto.WynkResponse;
import in.wynk.payment.dto.request.CombinedPaymentDetailsRequest;
import in.wynk.payment.dto.response.PaymentDetailsWrapper;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.session.aspect.advice.ManageSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wynk/v1/payment")
public class PaymentOptionsController {

    @Autowired
    private IPaymentOptionService paymentMethodService;

    @GetMapping("/options/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentOptions")
    public PaymentOptionsDTO getPaymentMethods(@PathVariable String sid, @RequestParam String planId) {
        return paymentMethodService.getPaymentOptions(planId);
    }

    @PostMapping("/details/combine/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "combinedPaymentDetails")
    public WynkResponse<PaymentDetailsWrapper> getPaymentDetails(@PathVariable String sid, @RequestBody CombinedPaymentDetailsRequest request) {
        return WynkResponse.<PaymentDetailsWrapper>builder().body(paymentMethodService.getPaymentDetails(request)).build();
    }

}
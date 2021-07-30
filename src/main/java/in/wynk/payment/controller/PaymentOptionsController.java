package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.AbstractProductDetails;
import in.wynk.payment.dto.request.CombinedPaymentDetailsRequest;
import in.wynk.payment.dto.response.CombinedPaymentDetailsResponse;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.IUserPreferredPaymentService;
import in.wynk.session.aspect.advice.ManageSession;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v1/payment")
public class PaymentOptionsController {

    private final IPaymentOptionService paymentMethodService;
    private final IUserPreferredPaymentService<CombinedPaymentDetailsResponse, CombinedPaymentDetailsRequest<?>> preferredPaymentService;

    @GetMapping("/options/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentOptions")
    public PaymentOptionsDTO getPaymentMethods(@PathVariable String sid, @RequestParam(defaultValue = "") String planId, @RequestParam(defaultValue = "") String itemId) {
        if(StringUtils.isEmpty(planId) && StringUtils.isEmpty(itemId)) throw new WynkRuntimeException("planId or itemId is not supplied or found empty");
        return paymentMethodService.getPaymentOptions(planId, itemId);
    }

    @PostMapping("/saved/details/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "savedDetails")
    public WynkResponseEntity<CombinedPaymentDetailsResponse> getPaymentDetails(@PathVariable String sid, @RequestBody CombinedPaymentDetailsRequest<? extends AbstractProductDetails> request) {
        AnalyticService.update(request);
        WynkResponseEntity<CombinedPaymentDetailsResponse> detailsResponse = preferredPaymentService.getUserPreferredPayments(request);
        AnalyticService.update(detailsResponse.getBody());
        return detailsResponse;
    }

}
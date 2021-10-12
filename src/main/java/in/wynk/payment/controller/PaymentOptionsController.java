package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.AbstractProductDetails;
import in.wynk.payment.dto.WebPaymentOptionsRequest;
import in.wynk.payment.dto.request.CombinedWebPaymentDetailsRequest;
import in.wynk.payment.dto.request.DefaultPaymentOptionRequest;
import in.wynk.payment.dto.response.CombinedPaymentDetailsResponse;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.IUserPreferredPaymentService;
import in.wynk.session.aspect.advice.ManageSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

import static in.wynk.common.constant.CacheBeanNameConstants.ITEM_DTO;
import static in.wynk.common.constant.CacheBeanNameConstants.PLAN_DTO;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v1/payment")
public class PaymentOptionsController {

    private final IPaymentOptionService paymentMethodService;
    private final IUserPreferredPaymentService<CombinedPaymentDetailsResponse, CombinedWebPaymentDetailsRequest<?>> preferredPaymentService;

    @GetMapping("/options/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentOptions")
    public PaymentOptionsDTO getPaymentMethods(@PathVariable String sid, @RequestParam(required = false) @MongoBaseEntityConstraint(beanName = PLAN_DTO) Integer planId, @RequestParam(required = false) @MongoBaseEntityConstraint(beanName = ITEM_DTO) String itemId) {
        if (Objects.isNull(planId) && Objects.isNull(itemId))
            throw new WynkRuntimeException("planId or itemId is not supplied or found empty");
        return paymentMethodService.getPaymentOptions(planId.toString(), itemId);
    }

    @PostMapping("/options/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentOptions")
    public PaymentOptionsDTO getPaymentMethodsV2(@PathVariable String sid, @RequestBody DefaultPaymentOptionRequest<WebPaymentOptionsRequest> request) {
        AnalyticService.update(request);
        return paymentMethodService.getFilteredPaymentOptions(request);
    }

    @ManageSession(sessionId = "#sid")
    @PostMapping("/saved/details/{sid}")
    @AnalyseTransaction(name = "savedDetails")
    public WynkResponseEntity<CombinedPaymentDetailsResponse> getPaymentDetails(@PathVariable String sid, @RequestBody CombinedWebPaymentDetailsRequest<? extends AbstractProductDetails> request) {
        AnalyticService.update(request);
        WynkResponseEntity<CombinedPaymentDetailsResponse> detailsResponse = preferredPaymentService.getUserPreferredPayments(request);
        AnalyticService.update(detailsResponse.getBody());
        return detailsResponse;
    }

}
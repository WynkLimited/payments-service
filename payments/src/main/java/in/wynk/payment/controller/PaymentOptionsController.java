package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.WebPaymentOptionsRequest;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.request.AbstractPreferredPaymentDetailsControllerRequest;
import in.wynk.payment.dto.request.CombinedWebPaymentDetailsRequest;
import in.wynk.payment.dto.response.CombinedPaymentDetailsResponse;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.IUserPreferredPaymentService;
import in.wynk.payment.utils.LoadClientUtils;
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
    private final IUserPreferredPaymentService<CombinedPaymentDetailsResponse, AbstractPreferredPaymentDetailsControllerRequest<?>> preferredPaymentService;

    @GetMapping("/options/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentOptions")
    public WynkResponseEntity<PaymentOptionsDTO>  getPaymentMethods(@PathVariable String sid, @RequestParam(required = false) @MongoBaseEntityConstraint(beanName = PLAN_DTO) Integer planId, @RequestParam(required = false) @MongoBaseEntityConstraint(beanName = ITEM_DTO) String itemId) {
        LoadClientUtils.loadClient(false);
        if (Objects.isNull(planId) && Objects.isNull(itemId))
            throw new WynkRuntimeException("planId or itemId is not supplied or found empty");
        return paymentMethodService.getPaymentOptions(planId.toString(), itemId);
    }

    @PostMapping("/options/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentOptions")
    public WynkResponseEntity<PaymentOptionsDTO> getPaymentMethodsV2(@PathVariable String sid, @RequestBody AbstractPaymentOptionsRequest<WebPaymentOptionsRequest> request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        return paymentMethodService.getFilteredPaymentOptions(request);
    }

    @ManageSession(sessionId = "#sid")
    @PostMapping("/saved/details/{sid}")
    @AnalyseTransaction(name = "savedDetails")
    public WynkResponseEntity<CombinedPaymentDetailsResponse> getPaymentDetails(@PathVariable String sid, @RequestBody CombinedWebPaymentDetailsRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        WynkResponseEntity<CombinedPaymentDetailsResponse> detailsResponse = preferredPaymentService.getUserPreferredPayments(request);
        AnalyticService.update(detailsResponse.getBody());
        return detailsResponse;
    }

}
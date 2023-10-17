package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.IPaymentOptionsRequest;
import in.wynk.payment.dto.WebPaymentOptionsRequest;
import in.wynk.payment.dto.common.FilteredPaymentOptionsResult;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.response.paymentoption.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionServiceV2;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.web.bind.annotation.*;

/**
 * @author Nishesh Pandey
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v2/payment")
public class PaymentOptionsControllerV2 {

    private final IPaymentOptionServiceV2 service;

    /**
     * This Api returns payments eligible payment methods and groups having minimum 1 payment method
     * @param sid
     * @param request
     * @return
     */
    @PostMapping("/options/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentOptions")
    public WynkResponseEntity<PaymentOptionsDTO> getPaymentMethods(@PathVariable String sid, @RequestBody AbstractPaymentOptionsRequest<WebPaymentOptionsRequest> request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        return BeanLocatorFactory.getBean(new ParameterizedTypeReference<IWynkPresentation<PaymentOptionsDTO, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>>>() {
        }).transform(() -> Pair.of(request.getPaymentOptionRequest(), service.getPaymentOptions(request)));
    }
}

package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.IPaymentOptionsRequest;
import in.wynk.payment.dto.S2SPaymentOptionsRequest;
import in.wynk.payment.dto.common.FilteredPaymentOptionsResult;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.response.paymentoption.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionServiceV2;
import in.wynk.payment.utils.LoadClientUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/s2s/v2/payment")
public class PaymentOptionsS2SControllerV2 {

    private final IPaymentOptionServiceV2 service;

    @PostMapping("/options")
    @AnalyseTransaction(name = "paymentOptions")
    public WynkResponseEntity<PaymentOptionsDTO> getFilteredPaymentMethods(@RequestBody AbstractPaymentOptionsRequest<S2SPaymentOptionsRequest> request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(request);
        return BeanLocatorFactory.getBean(new ParameterizedTypeReference<IWynkPresentation<in.wynk.payment.dto.response.paymentoption.PaymentOptionsDTO, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>>>() {
        }).transform(() -> Pair.of(request.getPaymentOptionRequest(), service.getPaymentOptions(request)));
    }

}

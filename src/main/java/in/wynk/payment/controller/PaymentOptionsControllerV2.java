package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.WebPaymentOptionsRequest;
import in.wynk.payment.dto.request.DefaultPaymentOptionRequest;
import in.wynk.payment.dto.response.paymentoption.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionServiceV2;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

import static in.wynk.common.constant.CacheBeanNameConstants.ITEM_DTO;
import static in.wynk.common.constant.CacheBeanNameConstants.PLAN_DTO;

/**
 * @author Nishesh Pandey
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v2/payment")
public class PaymentOptionsControllerV2 {
    private final IPaymentOptionServiceV2 paymentMethodServiceV2;

    /**
     * This Api returns payments eligible payment methods and groups having minimum 1 payment method
     */
    @PostMapping("/options/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentOptions")
    public WynkResponseEntity<PaymentOptionsDTO> getPaymentMethods(@PathVariable String sid, @RequestBody DefaultPaymentOptionRequest<WebPaymentOptionsRequest> request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        WynkResponseEntity.WynkResponseEntityBuilder<PaymentOptionsDTO> responseEntityBuilder = WynkResponseEntity.<PaymentOptionsDTO>builder();
        return responseEntityBuilder.status(HttpStatus.OK).data(paymentMethodServiceV2.getPaymentOptions(request)).build();
    }
}

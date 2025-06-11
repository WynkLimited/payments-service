package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.ChargingTransactionStatusRequest;
import in.wynk.payment.dto.request.S2SChargingRequestV2;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import in.wynk.payment.presentation.IPaymentPresentationV2;
import in.wynk.payment.presentation.dto.charge.PaymentChargingResponse;
import in.wynk.payment.presentation.dto.status.PaymentStatusResponse;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.utils.LoadClientUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_CLIENT_AUTHORIZATION;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import in.wynk.payment.service.IPaymentRenewalInfoService;

/**
 * @author Nishesh Pandey
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v2/s2s/payment")
public class RevenuePaymentS2SControllerV2 {

    private final PaymentGatewayManager manager;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final IPaymentRenewalInfoService paymentRenewalInfoService;

    @PostMapping("/charge")
    @AnalyseTransaction(name = "paymentCharging")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"PAYMENT_CHARGING_WRITE\")")
    public WynkResponseEntity<PaymentChargingResponse> doCharging (@Valid @RequestBody S2SChargingRequestV2 request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(PAYMENT_METHOD, paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId()).getPaymentCode().name());
        AnalyticService.update(request);
        final WynkResponseEntity<PaymentChargingResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPaymentPresentationV2<PaymentChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>>>() {
                }).transform(() -> Pair.of(request, manager.charge(request)));
        AnalyticService.update(responseEntity);
        return responseEntity;
    }

    @GetMapping("/status/{tid}")
    @AnalyseTransaction(name = "paymentStatus")
    public WynkResponseEntity<PaymentStatusResponse> status (@PathVariable String tid) {
        LoadClientUtils.loadClient(true);
        final WynkResponseEntity<PaymentStatusResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IWynkPresentation<PaymentStatusResponse, AbstractPaymentStatusResponse>>() {
                }).transform(() -> manager.reconcile(ChargingTransactionStatusRequest.builder().transactionId(tid).build()));
        AnalyticService.update(responseEntity);
        return responseEntity;
    }

    @GetMapping("/event/{transactionId}")
    @AnalyseTransaction(name = "getMerchantTransactionEvent")
    public WynkResponseEntity<String> getMerchantTransactionEvent(@PathVariable String transactionId) {
        String event = paymentRenewalInfoService.getMerchantTransactionEvent(transactionId);
        return WynkResponseEntity.<String>builder().data(event).build();
    }

    @PostMapping("/refund")
    @AnalyseTransaction(name = "initRefund")
    @PreAuthorize(PAYMENT_CLIENT_AUTHORIZATION + " && hasAuthority(\"INIT_REFUND_WRITE\")")
    public WynkResponseEntity<AbstractPaymentRefundResponse> doRefundV2(@Valid @RequestBody PaymentRefundInitRequest request) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(request);
        AbstractPaymentRefundResponse response = manager.doRefund(request);
        AnalyticService.update(response);
        return WynkResponseEntity.<AbstractPaymentRefundResponse>builder().data(response).build();
    }
}

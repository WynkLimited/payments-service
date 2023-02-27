package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.request.AbstractChargingRequestV2;
import in.wynk.payment.dto.request.CallbackRequestWrapper;
import in.wynk.payment.dto.request.ChargingTransactionStatusRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.dto.response.IVerificationResponse;
import in.wynk.payment.presentation.PaymentCallbackPresentation;
import in.wynk.payment.presentation.dto.charge.PaymentChargingResponse;
import in.wynk.payment.presentation.dto.status.PaymentStatusResponse;
import in.wynk.payment.service.IMerchantVerificationService;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST_PAYLOAD;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v2/payment")
public class RevenuePaymentControllerV2 {

    private final Gson gson;
    private final PaymentGatewayManager manager;
    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;

    @PostMapping("/verify/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "verifyUserPaymentBin")
    public WynkResponseEntity<IVerificationResponse> verify (@PathVariable String sid, @Valid @RequestBody VerificationRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        WynkResponseEntity<IVerificationResponse> verificationResponseWynkResponseEntity =
                BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), IMerchantVerificationService.class).doVerify(request);
        AnalyticService.update(verificationResponseWynkResponseEntity.getBody().getData());
        return verificationResponseWynkResponseEntity;
    }

    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = "/callback/{sid}/{pc}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<AbstractPaymentCallbackResponse> handleCallback (@PathVariable String sid, @PathVariable String pc, @RequestParam Map<String, Object> payload) {
        LoadClientUtils.loadClient(false);
        final PaymentGateway paymentGateway;
        if (StringUtils.isEmpty(pc)) {
            final SessionDTO sessionDTO = SessionContextHolder.getBody();
            final String transactionId = sessionDTO.get(TRANSACTION_ID);
            payload.put(TRANSACTION_ID_FULL, transactionId);
            paymentGateway = PaymentCodeCachingService.getFromCode(sessionDTO.get(PAYMENT_CODE));
        } else {
            paymentGateway = PaymentCodeCachingService.getFromCode(pc);
        }
        final CallbackRequestWrapper<?> request = CallbackRequestWrapper.builder().paymentGateway(paymentGateway).payload(payload).build();
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        return BeanLocatorFactory.getBean(PaymentCallbackPresentation.class).transform(manager.handleCallback(request));
    }

    @ManageSession(sessionId = "#sid")
    @GetMapping(path = "/callback/{sid}/{pc}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<AbstractPaymentCallbackResponse> handleCallbackGet (@PathVariable String sid, @PathVariable String pc, @RequestParam MultiValueMap<String, String> payload) {
        LoadClientUtils.loadClient(false);
        final PaymentGateway paymentGateway;
        final Map<String, Object> terraformed = new HashMap<>(payload.toSingleValueMap());
        if (StringUtils.isEmpty(pc)) {
            final SessionDTO sessionDTO = SessionContextHolder.getBody();
            final String transactionId = sessionDTO.get(TRANSACTION_ID);
            terraformed.put(TRANSACTION_ID_FULL, transactionId);
            paymentGateway = PaymentCodeCachingService.getFromCode(sessionDTO.get(PAYMENT_CODE));
        } else {
            paymentGateway = PaymentCodeCachingService.getFromCode(pc);
        }
        final CallbackRequestWrapper<?> request = CallbackRequestWrapper.builder().paymentGateway(paymentGateway).payload(terraformed).build();
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        return BeanLocatorFactory.getBean(PaymentCallbackPresentation.class).transform(manager.handleCallback(request));
    }

    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = {"/callback/{sid}", "/callback/{sid}/{pc}"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public WynkResponseEntity<AbstractPaymentCallbackResponse> handleCallbackJSON (@PathVariable String sid, @PathVariable(required = false) String pc, @RequestBody Map<String, Object> payload) {
        LoadClientUtils.loadClient(false);
        final PaymentGateway paymentGateway;
        if (StringUtils.isEmpty(pc)) {
            final SessionDTO sessionDTO = SessionContextHolder.getBody();
            final String transactionId = sessionDTO.get(TRANSACTION_ID);
            payload.put(TRANSACTION_ID_FULL, transactionId);
            paymentGateway = PaymentCodeCachingService.getFromCode(sessionDTO.get(PAYMENT_CODE));
        } else {
            paymentGateway = PaymentCodeCachingService.getFromCode(pc);
        }
        final CallbackRequestWrapper<?> request = CallbackRequestWrapper.builder().paymentGateway(paymentGateway).payload(payload).build();
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        return BeanLocatorFactory.getBean(PaymentCallbackPresentation.class).transform(manager.handleCallback(request));
    }

    //This version is for payment refactoring task
    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public WynkResponseEntity<PaymentChargingResponse> doCharging (@PathVariable String sid, @RequestBody AbstractChargingRequestV2 request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(PAYMENT_METHOD, paymentMethodCache.get(request.getPaymentDetails().getPaymentId()).getPaymentCode().name());
        AnalyticService.update(request);
        final WynkResponseEntity<PaymentChargingResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IWynkPresentation<PaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>>() {
                }).transform(() -> Pair.of(request, manager.charge(request)));
        AnalyticService.update(responseEntity);
        return null;
    }

    //This version is for payment refactoring task
    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public WynkResponseEntity<PaymentStatusResponse> status (@PathVariable String sid) {
        LoadClientUtils.loadClient(false);
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final WynkResponseEntity<PaymentStatusResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IWynkPresentation<PaymentStatusResponse, AbstractPaymentStatusResponse>>() {
                }).transform(() -> manager.status(ChargingTransactionStatusRequest.builder().transactionId(sessionDTO.<String>get(TRANSACTION_ID)).build()));
        AnalyticService.update(responseEntity);
        return responseEntity;
    }
}
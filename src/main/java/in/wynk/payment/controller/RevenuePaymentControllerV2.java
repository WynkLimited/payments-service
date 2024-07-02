package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.common.response.AbstractPaymentAccountDeletionResponse;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.manager.CallbackResponseWrapper;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.response.IVerificationResponse;
import in.wynk.payment.gateway.aps.ApsOrderGateway;
import in.wynk.payment.presentation.IPaymentPresentation;
import in.wynk.payment.presentation.IPaymentPresentationV2;
import in.wynk.payment.presentation.dto.callback.PaymentCallbackResponse;
import in.wynk.payment.presentation.dto.charge.PaymentChargingResponse;
import in.wynk.payment.presentation.dto.status.PaymentStatusResponse;
import in.wynk.payment.service.IMerchantVerificationService;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URISyntaxException;
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
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final ApsOrderGateway apsOrderGateway;

    @PostMapping("/verify/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "verifyUserPaymentBin")
    public WynkResponseEntity<IVerificationResponse> verify (@PathVariable String sid, @Valid @RequestBody WebVerificationRequest request) {
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
    public WynkResponseEntity<PaymentCallbackResponse> handleCallback (@RequestHeader HttpHeaders headers, @PathVariable String sid, @PathVariable String pc, @RequestParam Map<String, Object> payload) throws URISyntaxException {
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
        final CallbackRequestWrapperV2<?> request = CallbackRequestWrapperV2.builder().paymentGateway(paymentGateway).payload(payload).headers(headers).build();
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        final WynkResponseEntity<PaymentCallbackResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPaymentPresentation<PaymentCallbackResponse, CallbackResponseWrapper<?>>>() {
                }).transform(manager.handle(request));
        AnalyticService.update(responseEntity);
        return responseEntity;
    }

    @SneakyThrows
    @ManageSession(sessionId = "#sid")
    @GetMapping(path = "/callback/{sid}/{pc}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<PaymentCallbackResponse> handleCallbackGet (@RequestHeader HttpHeaders headers, @PathVariable String sid, @PathVariable String pc, @RequestParam MultiValueMap<String, String> payload) {
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
        final CallbackRequestWrapperV2<?> request = CallbackRequestWrapperV2.builder().paymentGateway(paymentGateway).payload(terraformed).headers(headers).build();
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        final WynkResponseEntity<PaymentCallbackResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPaymentPresentation<PaymentCallbackResponse, CallbackResponseWrapper<?>>>() {
                }).transform(manager.handle(request));
        AnalyticService.update(responseEntity);
        return responseEntity;
    }

    /**This endpoint is used for redirection callback in case of card and netBanking**/
    @SneakyThrows
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = {"/callback/{sid}", "/callback/{sid}/{pc}"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public WynkResponseEntity<PaymentCallbackResponse> handleCallbackJSON (@RequestHeader HttpHeaders headers, @PathVariable String sid, @PathVariable(required = false) String pc, @RequestBody Map<String, Object> payload) {
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
        final CallbackRequestWrapperV2<?> request = CallbackRequestWrapperV2.builder().paymentGateway(paymentGateway).payload(payload).headers(headers).build();
        AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
        AnalyticService.update(REQUEST_PAYLOAD, gson.toJson(payload));
        final WynkResponseEntity<PaymentCallbackResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPaymentPresentation<PaymentCallbackResponse, CallbackResponseWrapper<?>>>() {
                }).transform(manager.handle(request));
        AnalyticService.update(responseEntity);
        return responseEntity;
    }

    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public WynkResponseEntity<PaymentChargingResponse> doCharging (@PathVariable String sid, @RequestBody WebChargingRequestV2 request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(PAYMENT_METHOD, paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId()).getPaymentCode().name());
        AnalyticService.update(request);
        final WynkResponseEntity<PaymentChargingResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPaymentPresentationV2<PaymentChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>>>() {
                }).transform(() -> Pair.of(request, manager.charge(request)));
        AnalyticService.update(responseEntity);
        return responseEntity;
    }

    @GetMapping("/test/cache")
    public void testCache(@RequestParam String msisdn, @RequestParam String planId) {
        apsOrderGateway.testAsync(msisdn, planId);
    }

    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public WynkResponseEntity<PaymentStatusResponse> status (@PathVariable String sid) {
        LoadClientUtils.loadClient(false);
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final WynkResponseEntity<PaymentStatusResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IWynkPresentation<PaymentStatusResponse, AbstractPaymentStatusResponse>>() {
                }).transform(() -> manager.reconcile(ChargingTransactionStatusRequest.builder().transactionId(sessionDTO.<String>get(TRANSACTION_ID)).build()));
        AnalyticService.update(responseEntity);
        return responseEntity;
    }

    @PostMapping("/delete/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "deletePaymentMethod")
    public WynkResponseEntity<AbstractPaymentAccountDeletionResponse> delete (@PathVariable String sid, @Valid @RequestBody WebPaymentAccountDeletionRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        return WynkResponseEntity.<AbstractPaymentAccountDeletionResponse>builder().data(manager.delete(request)).status(HttpStatus.OK).build();
    }
}
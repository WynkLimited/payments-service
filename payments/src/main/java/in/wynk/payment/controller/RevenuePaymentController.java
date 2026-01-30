package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.WebPaymentOptionsRequest;
import in.wynk.payment.dto.WebPurchaseDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.presentation.IPaymentPresentationV2;
import in.wynk.payment.presentation.dto.qrCode.QRCodeChargingResponse;
import in.wynk.payment.service.IMerchantVerificationService;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.service.PaymentManager;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/wynk/v1/payment")
public class RevenuePaymentController {

    private final Gson gson;
    private final PaymentManager paymentManager;
    private final WebRequestVersionConversion webRequestVersionConversion;
    private final PaymentGatewayManager paymentGatewayManager;

    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public WynkResponseEntity<AbstractChargingResponse> doCharging(@PathVariable String sid, @RequestBody AbstractChargingRequest<WebPurchaseDetails> request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        AnalyticService.update(request);
        return paymentManager.charge(request);
    }

    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public WynkResponseEntity<AbstractChargingStatusResponse> status(@PathVariable String sid) {
        LoadClientUtils.loadClient(false);
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        return paymentManager.status(sessionDTO.<String>get(TRANSACTION_ID));
    }

    @Deprecated
    @PostMapping("/verify/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "verifyUserPaymentBin")
    public ResponseEntity<IVerificationResponse> verify(@PathVariable String sid, @Valid @RequestBody WebVerificationRequest request) {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        AnalyticService.update(PAYMENT_METHOD, request.getPaymentCode().name());
        WynkResponseEntity<IVerificationResponse> verificationResponseWynkResponseEntity = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), IMerchantVerificationService.class).doVerify(request);
        BaseResponse<IVerificationResponse> verificationResponseBaseResponse = BaseResponse.<IVerificationResponse>builder().body(verificationResponseWynkResponseEntity.getBody().getData()).status(verificationResponseWynkResponseEntity.getStatus()).build();
        return verificationResponseBaseResponse.getResponse();
    }

    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = "/callback/{sid}/{pc}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback(@PathVariable String sid, @PathVariable String pc, @RequestParam Map<String, Object> payload) {
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
        return paymentManager.handleCallback(request);
    }

    @ManageSession(sessionId = "#sid")
    @GetMapping(path = "/callback/{sid}/{pc}")
    @AnalyseTransaction(name = "paymentCallback")
    public WynkResponseEntity<AbstractCallbackResponse> handleCallbackGet(@PathVariable String sid, @PathVariable String pc, @RequestParam MultiValueMap<String, String> payload) {
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
        return paymentManager.handleCallback(request);
    }

    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    @PostMapping(path = {"/callback/{sid}", "/callback/{sid}/{pc}"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public WynkResponseEntity<AbstractCallbackResponse> handleCallbackJSON(@PathVariable String sid, @PathVariable(required = false) String pc, @RequestBody Map<String, Object> payload) {
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
        return paymentManager.handleCallback(request);
    }

    @PostMapping("/qrcode/generate/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "generateQRCode")
    public WynkResponseEntity<QRCodeChargingResponse> generateQRCode(@PathVariable String sid, @RequestBody AbstractPaymentOptionsRequest<WebPaymentOptionsRequest> request) throws URISyntaxException {
        LoadClientUtils.loadClient(false);
        AnalyticService.update(request);
        final WebChargingRequestV2 webChargingRequestV2 = webRequestVersionConversion.transform(request);
        final WynkResponseEntity<QRCodeChargingResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPaymentPresentationV2<QRCodeChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>>>() {
                }).transform(() -> Pair.of(webChargingRequestV2, paymentGatewayManager.charge(webChargingRequestV2)));
        AnalyticService.update(responseEntity);
        return responseEntity;
    }
}
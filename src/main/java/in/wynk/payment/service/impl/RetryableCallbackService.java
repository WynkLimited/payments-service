package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.callback.DefaultPaymentCallbackResponse;
import in.wynk.payment.dto.manager.CallbackResponseWrapper;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.CallbackRequestWrapper;
import in.wynk.payment.dto.request.CallbackRequestWrapperV2;
import in.wynk.payment.dto.request.NotificationRequest;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.service.ICallbackService;
import in.wynk.payment.service.IHeaderCallbackService;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.service.PaymentManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.*;

import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryableCallbackService implements ICallbackService<Object, AbstractCallbackResponse>, IHeaderCallbackService<Object, AbstractPaymentCallbackResponse> {

    private static final List<String> RECEIPT_PROCESSING_PAYMENT_CODE = Arrays.asList(ITUNES, AMAZON_IAP, GOOGLE_IAP);
    private final PaymentManager oldManager;
    private final PaymentGatewayManager newManager;

    private final Map<Class<?>, ICallbackService<Object, AbstractCallbackResponse>> bodyDelegate = new HashMap() {
        {
            put(Map.class, new MapBasedCallback());
            put(String.class, new StringBasedCallback());
        }
    };

    private final Map<Class<?>, IHeaderCallbackService<Object, AbstractPaymentCallbackResponse>> headerDelegate = new HashMap() {
        {
            put(Map.class, new MapBasedCallback());
            put(String.class, new StringBasedCallback());
        }
    };

    @Override
    @AnalyseTransaction(name = "paymentCallback")
    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 100, multiplier = 2))
    public AbstractCallbackResponse handle(String clientAlias, String partner, Object payload) {
        return bodyDelegate.getOrDefault(payload.getClass(), bodyDelegate.get(Map.class)).handle(clientAlias, partner, payload);
    }

    @Override
    @AnalyseTransaction(name = "paymentCallback")
    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 100, multiplier = 2))
    public AbstractPaymentCallbackResponse handle(String clientAlias, String partner, HttpHeaders headers, Object payload) {
        return headerDelegate.getOrDefault(payload.getClass(), headerDelegate.get(Map.class)).handle(clientAlias, partner, headers, payload);
    }

    private class StringBasedCallback implements ICallbackService<String, AbstractCallbackResponse>, IHeaderCallbackService<String, AbstractPaymentCallbackResponse> {

        @Override
        @ClientAware(clientAlias = "#clientAlias")
        public AbstractCallbackResponse handle(String clientAlias, String partner, String payload) {
            final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
            AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
            AnalyticService.update(REQUEST_PAYLOAD, payload);
            if (!RECEIPT_PROCESSING_PAYMENT_CODE.contains(paymentGateway.name())) {
                try {
                    return bodyDelegate.get(Map.class).handle(clientAlias, partner, BeanLocatorFactory.getBean(ObjectMapper.class).readValue(payload, new TypeReference<HashMap<String, Object>>() {
                    }));
                } catch (JsonProcessingException e) {
                    throw new WynkRuntimeException("Malformed payload is posted", e);
                }
            }
            newManager.handleNotification(NotificationRequest.builder().paymentGateway(paymentGateway).payload(payload).clientAlias(clientAlias).build());
            return null;
        }

        @Override
        @ClientAware(clientAlias = "#clientAlias")
        public AbstractPaymentCallbackResponse handle(String clientAlias, String partner, HttpHeaders headers, String payload) {
            final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
            AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
            AnalyticService.update(REQUEST_PAYLOAD, payload);
            if (!RECEIPT_PROCESSING_PAYMENT_CODE.contains(paymentGateway.name())) {
                try {
                    return headerDelegate.get(Map.class).handle(clientAlias, partner, headers, BeanLocatorFactory.getBean(ObjectMapper.class).readValue(payload, new TypeReference<HashMap<String, Object>>() {
                    }));
                } catch (JsonProcessingException e) {
                    throw new WynkRuntimeException("Malformed payload is posted", e);
                }
            }
            newManager.handleNotification(NotificationRequest.builder().paymentGateway(paymentGateway).payload(payload).clientAlias(clientAlias).build());
            return null;
        }
    }

    private class MapBasedCallback implements ICallbackService<Map<String, Object>, AbstractCallbackResponse>, IHeaderCallbackService<Map<String, Object>, AbstractPaymentCallbackResponse> {

        @Override
        @ClientAware(clientAlias = "#clientAlias")
        public AbstractCallbackResponse handle(String clientAlias, String partner, Map<String, Object> payload) {
            final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
            return oldManager.handleCallback(CallbackRequestWrapper.builder().paymentGateway(paymentGateway).payload(payload).build()).getBody().getData();
        }

        @Override
        @ClientAware(clientAlias = "#clientAlias")
        public CallbackResponseWrapper<AbstractPaymentCallbackResponse> handle(String clientAlias, String partner, HttpHeaders headers, Map<String, Object> payload) {
            final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
            CallbackRequestWrapperV2<CallbackRequest> callbackRequest =
                    CallbackRequestWrapperV2.builder().paymentGateway(paymentGateway).payload(payload).headers(headers).build();
            if (Objects.isNull(callbackRequest.getTransactionId())) {
                return CallbackResponseWrapper.builder().callbackResponse(DefaultPaymentCallbackResponse.builder().build()).build();
            }
            return newManager.handle(callbackRequest);
        }
    }

}

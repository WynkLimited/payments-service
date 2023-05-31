package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.manager.CallbackResponseWrapper;
import in.wynk.payment.dto.request.CallbackRequestWrapper;
import in.wynk.payment.dto.request.CallbackRequestWrapperV2;
import in.wynk.payment.dto.request.NotificationRequest;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.service.IAsyncCallbackService;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.service.PaymentManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@Service
@RequiredArgsConstructor
public class ServerNotificationCallbackService implements IAsyncCallbackService<Object> {

    private static final List<String> RECEIPT_PROCESSING_PAYMENT_CODE = Arrays.asList(ITUNES, AMAZON_IAP, GOOGLE_IAP);

    private final PaymentManager oldManager;
    private final PaymentGatewayManager newManager;

    private final Map<Class<?>, ICallbackService<Object>> delegate = new HashMap() {
        {
            put(Map.class, new MapBasedCallback());
            put(String.class, new StringBasedCallback());
        }
    };

    @Async
    @Override
    public CompletableFuture<AbstractCallbackResponse> handle(String rid, String clientAlias, String partner, Object payload) {
        final CompletableFuture<AbstractCallbackResponse> future = new CompletableFuture<>();
        try {
            final AbstractCallbackResponse response = handleInternal(rid, clientAlias, partner, payload);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Async
    @Override
    public CompletableFuture<CallbackResponseWrapper<AbstractPaymentCallbackResponse>> handle(String rid, String clientAlias, String partner, HttpHeaders headers, Object payload) {
        final CompletableFuture<CallbackResponseWrapper<AbstractPaymentCallbackResponse>> future = new CompletableFuture<>();
        try {
            final CallbackResponseWrapper<AbstractPaymentCallbackResponse> response = handleInternal(rid, clientAlias, partner, headers, payload);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 200, multiplier = 2))
    public AbstractCallbackResponse handleInternal(String rid, String clientAlias, String partner, Object payload) {
        MDC.put(REQUEST_ID, rid);
        return delegate.get(payload.getClass()).handle(clientAlias, partner, payload);
    }

    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 200, multiplier = 2))
    public CallbackResponseWrapper<AbstractPaymentCallbackResponse> handleInternal(String rid, String clientAlias, String partner, HttpHeaders headers, Object payload) {
        MDC.put(REQUEST_ID, rid);
        return delegate.get(payload.getClass()).handle(clientAlias, partner, headers, payload);
    }

    private interface ICallbackService<T> {
        AbstractCallbackResponse handle(String clientAlias, String partner, T payload);

        CallbackResponseWrapper<AbstractPaymentCallbackResponse> handle(String clientAlias, String partner, HttpHeaders headers, T payload);
    }

    private class StringBasedCallback implements ICallbackService<String> {

        @Override
        public AbstractCallbackResponse handle(String clientAlias, String partner, String payload) {
            final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
            AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
            AnalyticService.update(REQUEST_PAYLOAD, payload);
            if (!RECEIPT_PROCESSING_PAYMENT_CODE.contains(paymentGateway.name())) {
                try {
                    return delegate.get(Map.class).handle(clientAlias, partner, BeanLocatorFactory.getBean(ObjectMapper.class).readValue(payload, new TypeReference<HashMap<String, Object>>() {
                    }));
                } catch (JsonProcessingException e) {
                    throw new WynkRuntimeException("Malformed payload is posted", e);
                }
            }
            newManager.handleNotification(NotificationRequest.builder().paymentGateway(paymentGateway).payload(payload).clientAlias(clientAlias).build());
            return null;
        }

        @Override
        public CallbackResponseWrapper<AbstractPaymentCallbackResponse> handle(String clientAlias, String partner, HttpHeaders headers, String payload) {
            final PaymentGateway paymentGateway = PaymentCodeCachingService.getFromCode(partner);
            AnalyticService.update(PAYMENT_METHOD, paymentGateway.name());
            AnalyticService.update(REQUEST_PAYLOAD, payload);
            if (!RECEIPT_PROCESSING_PAYMENT_CODE.contains(paymentGateway.name())) {
                try {
                    return delegate.get(Map.class).handle(clientAlias, partner, headers, BeanLocatorFactory.getBean(ObjectMapper.class).readValue(payload, new TypeReference<HashMap<String, Object>>() {
                    }));
                } catch (JsonProcessingException e) {
                    throw new WynkRuntimeException("Malformed payload is posted", e);
                }
            }
            newManager.handleNotification(NotificationRequest.builder().paymentGateway(paymentGateway).payload(payload).clientAlias(clientAlias).build());
            return null;
        }
    }

    private class MapBasedCallback implements ICallbackService<Map<String, Object>> {

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
            return newManager.handle(CallbackRequestWrapperV2.builder().paymentGateway(paymentGateway).payload(payload).headers(headers).build());
        }
    }

}

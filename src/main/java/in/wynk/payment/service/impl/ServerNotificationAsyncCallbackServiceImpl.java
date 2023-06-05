package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.service.IAsyncCallbackService;
import in.wynk.payment.service.ICallbackService;
import in.wynk.payment.service.IHeaderCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerNotificationAsyncCallbackServiceImpl implements IAsyncCallbackService<Object,AbstractCallbackResponse, AbstractPaymentCallbackResponse> {

    private final ICallbackService<Object, AbstractCallbackResponse> bodyCallbackService;
    private final IHeaderCallbackService<Object, AbstractPaymentCallbackResponse> headerCallbackService;

    @Async
    @Override
    public CompletableFuture<AbstractCallbackResponse> handle(String rid, String clientAlias, String partner, Object payload) {
        MDC.put(REQUEST_ID, rid);
        final CompletableFuture<AbstractCallbackResponse> future = new CompletableFuture<>();
        try {
            final AbstractCallbackResponse response = handle(clientAlias, partner, payload);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.S2S_PAYMENT_CALLBACK_FAILURE, "Unable to process callback for client " + clientAlias + " for pg " + partner, e);
            future.completeExceptionally(e);
        }
        return future;
    }

    @Async
    @Override
    public CompletableFuture<AbstractPaymentCallbackResponse> handle(String rid, String clientAlias, String partner, HttpHeaders headers, Object payload) {
        MDC.put(REQUEST_ID, rid);
        final CompletableFuture<AbstractPaymentCallbackResponse> future = new CompletableFuture<>();
        try {
            final AbstractPaymentCallbackResponse response = handle(clientAlias, partner, headers, payload);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.S2S_PAYMENT_CALLBACK_FAILURE, "Unable to process callback for client " + clientAlias + " for pg " + partner, e);
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public AbstractCallbackResponse handle(String clientAlias, String partner, Object payload) {
        return bodyCallbackService.handle(clientAlias, partner, payload);
    }

    @Override
    public AbstractPaymentCallbackResponse handle(String clientAlias, String partner, HttpHeaders headers, Object payload) {
        return headerCallbackService.handle(clientAlias, partner, headers, payload);
    }


}

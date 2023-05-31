package in.wynk.payment.service;

import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.manager.CallbackResponseWrapper;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import org.springframework.http.HttpHeaders;

import java.util.concurrent.CompletableFuture;

public interface IAsyncCallbackService<T> {

    CompletableFuture<AbstractCallbackResponse> handle(String rid, String clientAlias, String partner, T payload);

    CompletableFuture<CallbackResponseWrapper<AbstractPaymentCallbackResponse>> handle(String rid, String clientAlias, String partner, HttpHeaders headers, T payload);

}

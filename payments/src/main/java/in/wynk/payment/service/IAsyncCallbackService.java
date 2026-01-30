package in.wynk.payment.service;

import org.springframework.http.HttpHeaders;

import java.util.concurrent.CompletableFuture;

public interface IAsyncCallbackService<T, R, K> extends ICallbackService<T, R>, IHeaderCallbackService<T, K> {
    CompletableFuture<R> handle(String rid, String clientAlias, String partner, T payload);
    CompletableFuture<K> handle(String rid, String clientAlias, String partner, HttpHeaders headers,T payload);
}

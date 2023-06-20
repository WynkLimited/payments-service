package in.wynk.payment.service;

import org.springframework.http.HttpHeaders;

public interface IHeaderCallbackService<T, R> {
    R handle(String clientAlias, String partner, HttpHeaders headers, T payload);
}

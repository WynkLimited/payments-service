package in.wynk.payment.service;

public interface ICallbackService<T, R> {
    R handle(String clientAlias, String partner, T payload); //check
}

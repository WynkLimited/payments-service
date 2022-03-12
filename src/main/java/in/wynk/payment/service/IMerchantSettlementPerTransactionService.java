package in.wynk.payment.service;

public interface IMerchantSettlementPerTransactionService<R, T> {
    R settle(T request);
}

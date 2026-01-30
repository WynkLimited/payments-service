package in.wynk.payment.consumer;

/**
 * @author Nishesh Pandey
 */
public interface PaymentChargeHandler<K> {
    void charge (K request);
}

package in.wynk.payment.consumer;

public interface InvoiceHandler<T> {
    void generateInvoice(T dto);
    void processCallback (T dto);
}

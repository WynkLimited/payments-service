package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Invoice;
import in.wynk.payment.dto.invoice.GenerateInvoiceRequest;

public interface InvoiceManager {

    void generate(GenerateInvoiceRequest request);

    byte[] download(String txnId);

    Invoice getInvoice(String id);

    void save(Invoice invoice);
}

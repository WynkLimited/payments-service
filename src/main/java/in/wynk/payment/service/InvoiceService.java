package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Invoice;

import java.util.Optional;

public interface InvoiceService {

    void upsert(Invoice invoice);

    Optional<Invoice> getInvoice(String id);

    Optional<Invoice> getInvoiceByTransactionId(String transactionId);
}
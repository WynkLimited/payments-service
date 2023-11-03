package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Invoice;

import java.util.Optional;

public interface InvoiceService {

    Invoice upsert(Invoice invoice);

    Invoice getInvoice(String id);

    Invoice getInvoiceByTransactionId(String transactionId);
}
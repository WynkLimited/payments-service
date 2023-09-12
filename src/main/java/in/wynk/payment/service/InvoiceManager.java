package in.wynk.payment.service;

import in.wynk.payment.dto.invoice.CoreInvoiceDownloadResponse;
import in.wynk.payment.dto.invoice.GenerateInvoiceRequest;
import in.wynk.payment.dto.invoice.InvoiceCallbackRequest;

public interface InvoiceManager {
    void generate(GenerateInvoiceRequest request);
    CoreInvoiceDownloadResponse download(String txnId);
    void processCallback(InvoiceCallbackRequest request);
}

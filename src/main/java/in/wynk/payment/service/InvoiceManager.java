package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.InvoiceDetails;
import in.wynk.payment.dto.invoice.CoreInvoiceDownloadResponse;
import in.wynk.payment.dto.invoice.GenerateInvoiceRequest;
import in.wynk.payment.dto.invoice.InvoiceCallbackRequest;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;

public interface InvoiceManager {
    void generate(GenerateInvoiceRequest request);
    CoreInvoiceDownloadResponse download(String txnId);
    void processCallback(InvoiceCallbackRequest request);
}

package in.wynk.payment.dto.invoice;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InvoiceDownloadResponse {
    private byte[] pdfStream;
    private String invoiceNumber;
    private String txnId;
}
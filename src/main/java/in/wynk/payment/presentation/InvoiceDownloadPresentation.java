package in.wynk.payment.presentation;

import in.wynk.payment.dto.invoice.CoreInvoiceDownloadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceDownloadPresentation implements IPaymentPresentationV2<byte[], CoreInvoiceDownloadResponse> {
    @Override
    public byte[] transform(CoreInvoiceDownloadResponse payload) {
        return payload.getData();
        /*return InvoiceDownloadResponse.builder()
                .pdfStream(payload.getData())
                .invoiceNumber(payload.getInvoice().getId())
                .txnId(payload.getInvoice().getTransactionId())
                .build();*/
    }
}

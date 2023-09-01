package in.wynk.payment.dto.invoice;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.InvoiceDetails;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PublishInvoiceRequest {
    private MsisdnOperatorDetails operatorDetails;
    private IPurchaseDetails purchaseDetails;
    private TaxableResponse taxableResponse;
    private InvoiceDetails invoiceDetails;
    private String invoiceId;
    private String uid;
}

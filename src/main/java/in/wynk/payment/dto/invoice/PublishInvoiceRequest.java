package in.wynk.payment.dto.invoice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.InvoiceDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublishInvoiceRequest {
    private Transaction transaction;
    private MsisdnOperatorDetails operatorDetails;
    private IPurchaseDetails purchaseDetails;
    private TaxableRequest taxableRequest;
    private TaxableResponse taxableResponse;
    private InvoiceDetails invoiceDetails;
    private String invoiceId;
    private String uid;
    private String type;
    private String skipDelivery;
}

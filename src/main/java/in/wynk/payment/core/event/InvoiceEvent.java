package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.Invoice;
import lombok.Builder;
import lombok.Getter;
import java.util.Calendar;

@Getter
@Builder
@AnalysedEntity
public class InvoiceEvent {
    @Analysed
    private String clientAlias;
    @Analysed
    private String invoiceId;
    @Analysed
    private String transactionId;
    @Analysed
    private String invoiceExternalId;
    @Analysed
    private double amount;
    @Analysed
    private double taxAmount;
    @Analysed
    private double taxableValue;
    @Analysed
    private double cgst;
    @Analysed
    private double sgst;
    @Analysed
    private double igst;
    @Analysed
    private String state;
    @Analysed
    private long createdOn;
    @Analysed
    private long updatedOn;
    @Analysed
    private int retryCount;
    private boolean persisted;

    public static InvoiceEvent from(Invoice invoice, String clientAlias){
        return InvoiceEvent.builder()
                .clientAlias(clientAlias)
                .invoiceId(invoice.getId())
                .transactionId(invoice.getTransactionId())
                .invoiceExternalId(invoice.getInvoiceExternalId())
                .amount(invoice.getAmount())
                .taxAmount(invoice.getTaxAmount())
                .taxableValue(invoice.getTaxableValue())
                .cgst(invoice.getCgst())
                .sgst(invoice.getSgst())
                .igst(invoice.getIgst())
                .state(invoice.getStatus())
                .createdOn(invoice.getCreatedOn().getTimeInMillis())
                .updatedOn(invoice.getUpdatedOn().getTimeInMillis())
                .retryCount(invoice.getRetryCount())
                .persisted(invoice.isPersisted())
                .build();
    }
}
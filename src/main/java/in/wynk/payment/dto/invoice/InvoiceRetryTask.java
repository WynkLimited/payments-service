package in.wynk.payment.dto.invoice;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.event.InvoiceRetryEvent;
import in.wynk.payment.core.event.InvoiceRetryTaskEvent;
import in.wynk.scheduler.task.dto.ITaskEntity;
import lombok.*;

@Getter
@Builder
@ToString
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRetryTask implements ITaskEntity {

    @Analysed
    private String invoiceId;
    @Analysed
    private String msisdn;
    @Analysed
    private String transactionId;
    @Analysed
    private String clientAlias;

    @Override
    public String getTaskId() {
        return msisdn + BaseConstants.DELIMITER + invoiceId;
    }

    @Override
    public String getGroupId() {
        return PaymentConstants.INVOICE_RETRY;
    }

    public static InvoiceRetryTask from(InvoiceRetryEvent event) {
        return InvoiceRetryTask.builder()
                .invoiceId(event.getInvoiceId())
                .clientAlias(event.getClientAlias())
                .msisdn(event.getMsisdn())
                .transactionId(event.getTxnId())
                .build();
    }

    public InvoiceRetryTaskEvent fromSelf() {
        return new InvoiceRetryTaskEvent(invoiceId, msisdn, transactionId, clientAlias);
    }

}
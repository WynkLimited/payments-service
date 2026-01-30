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
    private String msisdn;
    @Analysed
    private String transactionId;
    @Analysed
    private String clientAlias;
    @Analysed
    private String skipDelivery;
    @Analysed
    private int retryCount = 0;

    @Override
    public String getTaskId() {
        return msisdn + BaseConstants.DELIMITER + clientAlias + BaseConstants.DELIMITER + transactionId + BaseConstants.DELIMITER + retryCount;
    }

    @Override
    public String getGroupId() {
        return PaymentConstants.INVOICE_RETRY;
    }

    public static InvoiceRetryTask from(InvoiceRetryEvent event) {
        return InvoiceRetryTask.builder()
                .clientAlias(event.getClientAlias())
                .msisdn(event.getMsisdn())
                .transactionId(event.getTxnId())
                .skipDelivery(event.getSkipDelivery())
                .retryCount(event.getRetryCount())
                .build();
    }

    public InvoiceRetryTaskEvent fromSelf() {
        return new InvoiceRetryTaskEvent(msisdn, transactionId, clientAlias, skipDelivery, retryCount);
    }

}
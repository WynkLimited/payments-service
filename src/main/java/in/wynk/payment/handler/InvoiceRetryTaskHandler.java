package in.wynk.payment.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.InvoiceState;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Invoice;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.invoice.InvoiceRetryTask;
import in.wynk.payment.service.InvoiceService;
import in.wynk.scheduler.task.dto.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
public class InvoiceRetryTaskHandler extends TaskHandler<InvoiceRetryTask> {

    private final InvoiceService invoiceService;
    private final ApplicationEventPublisher eventPublisher;

    public InvoiceRetryTaskHandler(ObjectMapper mapper, InvoiceService invoiceService, ApplicationEventPublisher applicationEventPublisher) {
        super(mapper);
        this.invoiceService = invoiceService;
        this.eventPublisher = applicationEventPublisher;
    }

    @Override
    public TypeReference<InvoiceRetryTask> getTaskType() {
        return new TypeReference<InvoiceRetryTask>() {
        };
    }

    @Override
    public String getName() {
        return PaymentConstants.INVOICE_RETRY;
    }

    @Override
    public boolean shouldTriggerExecute(InvoiceRetryTask task) {
        final Invoice invoice = invoiceService.getInvoiceByTransactionId(task.getTransactionId());
        final boolean shouldTriggerExecute = !invoice.getStatus().equalsIgnoreCase(InvoiceState.SUCCESS.name());
        if (!shouldTriggerExecute) log.info("skipping to drop retry as invoice has been successfully generated for {}", task);
        return !invoice.getStatus().equalsIgnoreCase(InvoiceState.SUCCESS.name());
    }

    @Override
    public void execute(InvoiceRetryTask task) {
        eventPublisher.publishEvent(task.fromSelf());
    }

}
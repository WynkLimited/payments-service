package in.wynk.payment.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.invoice.InvoiceRetryTask;
import in.wynk.scheduler.task.dto.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
public class InvoiceRetryTaskHandler extends TaskHandler<InvoiceRetryTask> {

    private final ApplicationEventPublisher eventPublisher;

    public InvoiceRetryTaskHandler(ObjectMapper mapper, ApplicationEventPublisher applicationEventPublisher) {
        super(mapper);
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
    public void execute(InvoiceRetryTask task) {
        eventPublisher.publishEvent(task.fromSelf());
    }

}
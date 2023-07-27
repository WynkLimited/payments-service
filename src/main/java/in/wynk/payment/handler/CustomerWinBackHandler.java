package in.wynk.payment.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.Message;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PurchaseRecord;
import in.wynk.payment.service.IQuickPayLinkGenerator;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.scheduler.task.dto.TaskHandler;
import in.wynk.sms.common.message.SmsNotificationMessage;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import static in.wynk.common.constant.BaseConstants.PLAN;

@Slf4j
public class CustomerWinBackHandler extends TaskHandler<PurchaseRecord> {

    private final ITransactionManagerService transactionManager;
    private final ApplicationEventPublisher eventPublisher;

    public CustomerWinBackHandler(ObjectMapper mapper, ITransactionManagerService transactionManager, ApplicationEventPublisher applicationEventPublisher) {
        super(mapper);
        this.transactionManager = transactionManager;
        this.eventPublisher = applicationEventPublisher;
    }

    @Override
    public TypeReference<PurchaseRecord> getTaskType() {
        return new TypeReference<PurchaseRecord>() {
        };
    }

    @Override
    public String getName() {
        return PaymentConstants.USER_WINBACK;
    }

    @Override
    public boolean shouldTriggerExecute(PurchaseRecord task) {
        final Transaction lastTransaction = transactionManager.get(task.getTransactionId());
        final boolean shouldTriggerExecute = lastTransaction.getStatus() != TransactionStatus.SUCCESS;
        if (!shouldTriggerExecute) log.info("skipping to drop msg as user has completed transaction for purchase record {}", task);
        return lastTransaction.getStatus() != TransactionStatus.SUCCESS;
    }

    @Override
    @AnalyseTransaction(name = "userChurnLocator")
    public void execute(PurchaseRecord task) {
        eventPublisher.publishEvent(task.fromSelf());
    }

}

package in.wynk.payment.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PurchaseRecord;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.scheduler.common.constant.SchedulerLoggingMarker;
import in.wynk.scheduler.task.dto.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;


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
    @AnalyseTransaction(name ="shouldTriggerExecute")
    public boolean shouldTriggerExecute(PurchaseRecord task) {
        log.info("QUARTS_DEBUG : shouldTriggerExecute for taskID {} having transactionID {}", task.getTaskId(), task.getTransactionId());
        final Transaction lastTransaction = transactionManager.get(task.getTransactionId());
        AnalyticService.update("lastTransaction",lastTransaction.getIdStr());
        AnalyticService.update("lastTransactionStatus",lastTransaction.getStatus().toString());
        AnalyticService.update("currentTransactionStatus",TransactionStatus.SUCCESS.toString());
        final boolean shouldTriggerExecute = lastTransaction.getStatus() != TransactionStatus.SUCCESS;
        AnalyticService.update("shouldTriggerExecute",shouldTriggerExecute);
        if (!shouldTriggerExecute) log.info("skipping to drop msg as user has completed transaction for purchase record {}", task);
        return lastTransaction.getStatus() != TransactionStatus.SUCCESS;
    }

    @Override
    @AnalyseTransaction(name = "userChurnLocator")
    public void execute(PurchaseRecord task) {
        try {
            log.info("QUARTS_DEBUG : inside execute method of userChurnLocator");
            AnalyticService.update("taskID", task.getTaskId());
            AnalyticService.update("transactionId", task.getTransactionId());
            AnalyticService.update("uid", task.getUid());
            eventPublisher.publishEvent(task.fromSelf());
        } catch (Exception e) {
            log.error(SchedulerLoggingMarker.SCHEDULER_JOB_EXECUTION_ERROR, "task {} is not able to execute having uid {}", task, task.getUid(), e);
            throw e;
        }
    }

}

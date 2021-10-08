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

import static in.wynk.common.constant.BaseConstants.PLAN;

@Slf4j
public class CustomerWinBackHandler extends TaskHandler<PurchaseRecord> {

    private final PaymentCachingService cachingService;
    private final IQuickPayLinkGenerator quickPayLinkGenerator;
    private final ISqsManagerService<Object> sqsManagerService;
    private final ITransactionManagerService transactionManager;

    public CustomerWinBackHandler(ObjectMapper mapper, IQuickPayLinkGenerator quickPayLinkGenerator, ISqsManagerService<Object> sqsManagerService, PaymentCachingService cachingService, ITransactionManagerService transactionManager) {
        super(mapper);
        this.cachingService = cachingService;
        this.sqsManagerService = sqsManagerService;
        this.transactionManager = transactionManager;
        this.quickPayLinkGenerator = quickPayLinkGenerator;
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
        AnalyticService.update(task);
        final String service = task.getProductDetails().getType().equalsIgnoreCase(PLAN) ? cachingService.getPlan(task.getProductDetails().getId()).getService() : cachingService.getItem(task.getProductDetails().getId()).getService();
        final WynkService wynkService = WynkServiceUtils.fromServiceId(service);
        final Message message = wynkService.getMessages().get(PaymentConstants.USER_WINBACK);
        final String tinyUrl = quickPayLinkGenerator.generate(task.getTransactionId(), task.getClientAlias(), task.getSid(), task.getAppDetails(), task.getProductDetails());
        final String terraformed = message.getMessage().replace("<link>", tinyUrl);
        sqsManagerService.publishSQSMessage(SmsNotificationMessage.builder().message(terraformed).msisdn(task.getMsisdn()).priority(message.getPriority()).service(wynkService.getId()).build());
    }

}

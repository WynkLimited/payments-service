package in.wynk.payment.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PurchaseRecord;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.scheduler.task.dto.TaskHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserChurnLocator extends TaskHandler<PurchaseRecord> {

    public static String GROUP_ID = "user-churn-locator";

    private final ITransactionManagerService transactionManager;

    public UserChurnLocator(ObjectMapper mapper, ITransactionManagerService transactionManager) {
        super(mapper);
        this.transactionManager = transactionManager;
    }

    @Override
    public TypeReference<PurchaseRecord> getTaskType() {
        return new TypeReference<PurchaseRecord>() {
        };
    }

    @Override
    public String getName() {
        return GROUP_ID;
    }

    @Override
    public void execute(PurchaseRecord entity) {
        final Transaction lastTransaction = transactionManager.get(entity.getTransactionId());
        if (lastTransaction.getStatus() != TransactionStatus.SUCCESS) {
            // build the payment url
            // build the tiny url
            // embed and shoot the sms
        }
    }
}

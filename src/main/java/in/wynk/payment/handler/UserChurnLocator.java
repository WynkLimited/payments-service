package in.wynk.payment.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PurchaseRecord;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.scheduler.task.dto.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import static in.wynk.common.constant.BaseConstants.*;

@Slf4j
public class UserChurnLocator extends TaskHandler<PurchaseRecord> {

    private final String winBackUrl;
    private final ITransactionManagerService transactionManager;
    private final ClientDetailsCachingService clientDetailsService;

    public UserChurnLocator(ObjectMapper mapper, @Value("${payment.api.endpoint.winBack}") String winBackUrl, ClientDetailsCachingService clientDetailsService, ITransactionManagerService transactionManager) {
        super(mapper);
        this.winBackUrl = winBackUrl;
        this.clientDetailsService = clientDetailsService;
        this.transactionManager = transactionManager;
    }

    @Override
    public TypeReference<PurchaseRecord> getTaskType() {
        return new TypeReference<PurchaseRecord>() {
        };
    }

    @Override
    public String getName() {
        return PaymentConstants.USER_CHURN_GROUP;
    }

    @Override
    public void execute(PurchaseRecord entity) {
        final Transaction lastTransaction = transactionManager.get(entity.getTransactionId());
        final Client clientDetails = clientDetailsService.getClientByAlias(lastTransaction.getClientAlias());
        if (lastTransaction.getStatus() != TransactionStatus.SUCCESS) {
            final String url = winBackUrl + SLASH + lastTransaction.getIdStr() + QUESTION_MARK + APP_ID + EQUAL + clientDetails.getClientId() + AND + TOKEN_ID + EQUAL + EncryptionUtils.generateAppToken(lastTransaction.getIdStr(), clientDetails.getClientSecret());
            // build the payment url with auth params and dropped transaction id ?auth=signed transaction
            // build the tiny url
            // embed and shoot the sms
        }
    }
}

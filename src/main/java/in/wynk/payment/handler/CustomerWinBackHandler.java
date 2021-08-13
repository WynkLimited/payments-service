package in.wynk.payment.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.Message;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PurchaseRecord;
import in.wynk.payment.dto.UrlShortenRequest;
import in.wynk.payment.dto.UrlShortenResponse;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.IUrlShortenService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.scheduler.task.dto.TaskHandler;
import in.wynk.sms.common.message.SmsNotificationMessage;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import static in.wynk.common.constant.BaseConstants.*;

@Slf4j
public class CustomerWinBackHandler extends TaskHandler<PurchaseRecord> {

    private final String winBackUrl;
    private final ISqsManagerService sqsManagerService;
    private final IUrlShortenService urlShortenService;
    private final PaymentCachingService cachingService;
    private final ITransactionManagerService transactionManager;
    private final ClientDetailsCachingService clientDetailsService;

    public CustomerWinBackHandler(ObjectMapper mapper, @Value("${payment.api.endpoint.winBack}") String winBackUrl, ISqsManagerService sqsManagerService, IUrlShortenService urlShortenService, PaymentCachingService cachingService, ClientDetailsCachingService clientDetailsService, ITransactionManagerService transactionManager) {
        super(mapper);
        this.winBackUrl = winBackUrl;
        this.cachingService = cachingService;
        this.sqsManagerService = sqsManagerService;
        this.urlShortenService = urlShortenService;
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
    @ClientAware(clientAlias = "#task.clientAlias")
    @AnalyseTransaction(name = "userChurnLocator")
    public void execute(PurchaseRecord task) {
        AnalyticService.update(task);
        final Client clientDetails = ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
        final String service = task.getProductDetails().getType().equalsIgnoreCase(PLAN) ? cachingService.getPlan(task.getProductDetails().getId()).getService() : cachingService.getItem(task.getProductDetails().getId()).getService();
        final WynkService wynkService = WynkServiceUtils.fromServiceId(service);
        final Message message = wynkService.getMessages().get(PaymentConstants.USER_WINBACK);
        final String payUrl = winBackUrl + SLASH + task.getTransactionId() + QUESTION_MARK + CLIENT_IDENTITY + EQUAL + clientDetails.getClientId() + AND + TOKEN_ID + EQUAL + EncryptionUtils.generateAppToken(task.getTransactionId(), clientDetails.getClientSecret());
        final String finalPayUrl = wynkService.get(PaymentConstants.PAY_OPTION_DEEPLINK).map(deeplink -> deeplink + payUrl).orElse(payUrl);
        final UrlShortenResponse shortenResponse = urlShortenService.generate(UrlShortenRequest.builder().campaign(PaymentConstants.WINBACK_CAMPAIGN).channel(wynkService.getId()).data(finalPayUrl).build());
        final String terraformed = message.getMessage().replace("<link>", shortenResponse.getTinyUrl());
        sqsManagerService.publishSQSMessage(SmsNotificationMessage.builder().message(terraformed).msisdn(task.getMsisdn()).priority(message.getPriority()).service(wynkService.getId()).build());
    }
}

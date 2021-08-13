package in.wynk.payment.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.common.dto.Message;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IAppDetails;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;

@Slf4j
public class CustomerWinBackHandler extends TaskHandler<PurchaseRecord> {

    private final String winBackUrl;
    private final ISqsManagerService sqsManagerService;
    private final IUrlShortenService urlShortenService;
    private final PaymentCachingService cachingService;
    private final ITransactionManagerService transactionManager;

    public CustomerWinBackHandler(ObjectMapper mapper, @Value("${service.payment.api.endpoint.winBack}") String winBackUrl, ISqsManagerService sqsManagerService, IUrlShortenService urlShortenService, PaymentCachingService cachingService, ITransactionManagerService transactionManager) {
        super(mapper);
        this.winBackUrl = winBackUrl;
        this.cachingService = cachingService;
        this.sqsManagerService = sqsManagerService;
        this.urlShortenService = urlShortenService;
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

    @SneakyThrows
    @Override
    @ClientAware(clientAlias = "#task.clientAlias")
    @AnalyseTransaction(name = "userChurnLocator")
    public void execute(PurchaseRecord task) {
        AnalyticService.update(task);
        final Client clientDetails = ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
        final String service = task.getProductDetails().getType().equalsIgnoreCase(PLAN) ? cachingService.getPlan(task.getProductDetails().getId()).getService() : cachingService.getItem(task.getProductDetails().getId()).getService();
        final WynkService wynkService = WynkServiceUtils.fromServiceId(service);
        final Message message = wynkService.getMessages().get(PaymentConstants.USER_WINBACK);
        final long ttl = System.currentTimeMillis()  + TimeUnit.DAYS.toMillis(3);
        final String payUrl = buildUrlFrom(winBackUrl + task.getTransactionId() + QUESTION_MARK + CLIENT_IDENTITY + EQUAL + Base64.getEncoder().encodeToString(clientDetails.getAlias().getBytes(StandardCharsets.UTF_8)) + AND + TTL + EQUAL + ttl + AND + TOKEN_ID + EQUAL + URLEncoder.encode(EncryptionUtils.generateAppToken(task.getTransactionId() + COLON +ttl, clientDetails.getClientSecret()), StandardCharsets.UTF_8.toString()), task.getAppDetails());
        final String finalPayUrl = wynkService.get(PaymentConstants.PAY_OPTION_DEEPLINK).map(deeplink -> deeplink + payUrl).orElse(payUrl);
        final UrlShortenResponse shortenResponse = urlShortenService.generate(UrlShortenRequest.builder().campaign(PaymentConstants.WINBACK_CAMPAIGN).channel(wynkService.getId()).data(finalPayUrl).build());
        final String terraformed = message.getMessage().replace("<link>", shortenResponse.getTinyUrl());
        sqsManagerService.publishSQSMessage(SmsNotificationMessage.builder().message(terraformed).msisdn(task.getMsisdn()).priority(message.getPriority()).service(wynkService.getId()).build());
    }

    private String buildUrlFrom(String url, IAppDetails appDetails) {
        return url + AND + OS + EQUAL + appDetails.getOs() + AND + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }
}

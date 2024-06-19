package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.audit.IAuditableListener;
import in.wynk.audit.constant.AuditConstants;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.Utils;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.lock.WynkRedisLockService;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.service.GSTStateCodesCachingService;
import in.wynk.payment.core.service.InvoiceDetailsCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.gpbs.*;
import in.wynk.payment.dto.gpbs.acknowledge.queue.PurchaseAcknowledgeMessageManager;
import in.wynk.payment.dto.gpbs.acknowledge.request.*;
import in.wynk.payment.dto.gpbs.notification.request.DeveloperNotification;
import in.wynk.payment.dto.gpbs.notification.request.GooglePlayNotificationMessage;
import in.wynk.payment.dto.gpbs.notification.request.SubscriptionNotification;
import in.wynk.payment.dto.gpbs.request.*;
import in.wynk.payment.dto.gpbs.request.externalTransaction.ExternalSubscription;
import in.wynk.payment.dto.gpbs.request.externalTransaction.ExternalTransactionAddress;
import in.wynk.payment.dto.gpbs.request.externalTransaction.OneTimeExternalTransaction;
import in.wynk.payment.dto.gpbs.request.externalTransaction.RecurringExternalTransaction;
import in.wynk.payment.dto.gpbs.response.externalTransaction.GooglePlayReportResponse;
import in.wynk.payment.dto.gpbs.response.receipt.AbstractGooglePlayReceiptVerificationResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayProductReceiptResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlaySubscriptionReceiptResponse;
import in.wynk.payment.dto.invoice.TaxableRequest;
import in.wynk.payment.dto.invoice.TaxableResponse;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.MerchantServiceUtil;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static in.wynk.common.constant.BaseConstants.DEFAULT_ACCESS_STATE_CODE;
import static in.wynk.common.enums.PaymentEvent.*;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE;
import static in.wynk.payment.dto.gpbs.GooglePlayConstant.*;

/**
 * @author Nishesh Pandey
 */

@Slf4j
@Service(BeanConstant.GOOGLE_PLAY)
public class GooglePlayMerchantPaymentService extends AbstractMerchantPaymentStatusService
        implements IMerchantIapSubscriptionAcknowledgementService, IMerchantIapPaymentPreVerificationService, IMerchantIapPaymentVerificationService,
        IPaymentNotificationService<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>>,
        IReceiptDetailService<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>, GooglePlayCallbackRequest>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>,
        IMerchantIapSubscriptionCancellationService {
    @Value("${payment.googlePlay.baseUrl}")
    private String baseUrl;
    @Value("${payment.googlePlay.purchaseUrl.subscription}")
    private String subscriptionPurchase;
    @Value("${payment.googlePlay.purchaseUrl.product}")
    private String productPurchase;
    @Value("${payment.googlePlay.purchaseUrl.external}")
    private String externalPurchase;
    @Value("${payment.googlePlay.music.key}")
    private String musicKey;
    @Value("${payment.googlePlay.airteltv.key}")
    private String airtelTvKey;
    @Value("${payment.googlePlay.mockUrl}")
    private String mockUrl;

    private final Gson gson;
    private final RestTemplate restTemplate;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final WynkRedisLockService wynkRedisLockService;
    private GooglePlayCacheService googlePlayCacheService;
    private ISqsManagerService sqsMessagePublisher;
    private IAuditableListener auditingListener;
    private final IUserDetailsService userDetailsService;
    private final ITaxManager taxManager;
    private final GSTStateCodesCachingService stateCodesCachingService;
    private final InvoiceDetailsCachingService invoiceDetailsCachingService;

    public GooglePlayMerchantPaymentService (@Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, Gson gson,
                                             ApplicationEventPublisher eventPublisher, WynkRedisLockService wynkRedisLockService, IErrorCodesCacheService errorCodesCacheServiceImpl,
                                             GooglePlayCacheService googlePlayCacheService, PaymentCachingService cachingService,
                                             ISqsManagerService sqsMessagePublisher, @Qualifier(AuditConstants.MONGO_AUDIT_LISTENER) IAuditableListener auditingListener,
                                             IUserDetailsService userDetailsService, ITaxManager taxManager,
                                             GSTStateCodesCachingService stateCodesCachingService, InvoiceDetailsCachingService invoiceDetailsCachingService) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.gson = gson;
        this.restTemplate = restTemplate;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.wynkRedisLockService = wynkRedisLockService;
        this.googlePlayCacheService = googlePlayCacheService;
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.auditingListener = auditingListener;
        this.userDetailsService = userDetailsService;
        this.taxManager = taxManager;
        this.stateCodesCachingService = stateCodesCachingService;
        this.invoiceDetailsCachingService = invoiceDetailsCachingService;
    }

    @Deprecated
    @Override
    public GooglePlayLatestReceiptResponse getLatestReceiptResponse (IapVerificationRequest iapVerificationRequest) {
        return null;
    }

    @Override
    public void handleNotification (Transaction transaction, UserPlanMapping<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>> mapping) {
        GooglePlayLatestReceiptResponse receiptInfo = mapping.getMessage().getFirst();
        long expireTimestamp = 0;
        String productType = (mapping.getPlanId() != -1 && StringUtils.isEmpty(mapping.getItemId()) ? BaseConstants.PLAN : BaseConstants.POINT);
        if (BaseConstants.PLAN.equals(productType)) {
            GooglePlaySubscriptionReceiptResponse googlePlayResponse = (GooglePlaySubscriptionReceiptResponse) receiptInfo.getGooglePlayResponse();
            expireTimestamp = NumberUtils.toLong(googlePlayResponse.getExpiryTimeMillis());
        }
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        if ((expireTimestamp != 0 && (expireTimestamp > System.currentTimeMillis() || (transaction.getType() == CANCELLED && expireTimestamp <= System.currentTimeMillis()))) ||
                (expireTimestamp == 0 && StringUtils.isNotEmpty(mapping.getItemId()))) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
        }
        transaction.setStatus(finalTransactionStatus.name());
        saveReceipt(transaction, receiptInfo, productType);
    }

    @Override
    public UserPlanMapping<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>> getUserPlanMapping (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper) {
        GooglePlayCallbackRequest callbackRequest = wrapper.getDecodedNotification();
        //verify the receipt from server and then add txnType to mapping
        Optional<ReceiptDetails> optionalReceiptDetails =
                RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                        .findById(callbackRequest.getPurchaseToken());
        if (optionalReceiptDetails.isPresent()) {
            ReceiptDetails receiptDetails = optionalReceiptDetails.get();
            String productType = receiptDetails.getPlanId() == 0 ? BaseConstants.POINT : BaseConstants.PLAN;
            AbstractGooglePlayReceiptVerificationResponse abstractGooglePlayReceiptVerificationResponse =
                    googlePlayResponse(callbackRequest.getPurchaseToken(), callbackRequest.getSubscriptionId(), callbackRequest.getPackageName(),
                            MerchantServiceUtil.getService(callbackRequest.getPackageName()), productType);
            assert abstractGooglePlayReceiptVerificationResponse != null;
            LatestReceiptResponse latestReceiptResponse = mapGoogleResponseToReceiptResponse(abstractGooglePlayReceiptVerificationResponse, createRequest(callbackRequest), productType);
            final GooglePlayLatestReceiptResponse latestReceipt = (GooglePlayLatestReceiptResponse) latestReceiptResponse;
            //set the latest response to be used while deciding payment event
            wrapper.getDecodedNotification().setGooglePlayLatestReceiptResponse(latestReceipt);

            AnalyticService.update(GOOGLE_PLAY_RECEIPT, gson.toJson(latestReceipt));

            GooglePlayReceiptDetails googlePlayReceiptDetails = (GooglePlayReceiptDetails) receiptDetails;

            if ((googlePlayReceiptDetails.getSubscriptionId().equals(abstractGooglePlayReceiptVerificationResponse.getOrderId())) ||
                    (BaseConstants.PLAN.equals(productType) &&
                            Objects.equals(String.valueOf(receiptDetails.getExpiry()), ((GooglePlaySubscriptionReceiptResponse) latestReceipt.getGooglePlayResponse()).getExpiryTimeMillis()) &&
                            Objects.equals(receiptDetails.getNotificationType(), latestReceipt.getNotificationType()))) {
                log.info("Notification is already processed for the purchase token {}", latestReceipt.getPurchaseToken());
                return null;
            }
            UserPlanMapping.UserPlanMappingBuilder<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>> builder =
                    UserPlanMapping.<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>>builder().msisdn(receiptDetails.getMsisdn()).uid(receiptDetails.getUid())
                            .linkedTransactionId(receiptDetails.getPaymentTransactionId())
                            .message(Pair.of(latestReceipt, receiptDetails));
            if (BaseConstants.PLAN.equals(productType)) {
                PlanDTO planDTO = cachingService.getPlanFromSku(googlePlayReceiptDetails.getSkuId());
                GooglePlaySubscriptionReceiptResponse googlePlayReceiptResponse = ((GooglePlaySubscriptionReceiptResponse) latestReceipt.getGooglePlayResponse());
                boolean isFreeTrial =
                        Objects.equals(googlePlayReceiptResponse.getPaymentState(), FREE_TRIAL_PAYMENT_STATE) || FREE_TRIAL_AMOUNT.equals(googlePlayReceiptResponse.getPriceAmountMicros());
                //if free trial plan applied, perform events on that plan
                if (isFreeTrial) {
                    if (planDTO.getLinkedFreePlanId() != -1) {
                        return builder.planId(planDTO.getLinkedFreePlanId()).service(planDTO.getService()).build();
                    } else {
                        log.error("No Free Trial mapping present for planId {}", planDTO.getId());
                        throw new WynkRuntimeException(PaymentErrorType.PAY033);
                    }
                }
                return builder.planId(planDTO.getId()).service(planDTO.getService()).build();
            }
            return builder.itemId(receiptDetails.getItemId()).service(receiptDetails.getService()).build();
        }
        return null;
    }

    private GooglePlayVerificationRequest createRequest (GooglePlayCallbackRequest callbackRequest) {
        GooglePlayVerificationRequest request = new GooglePlayVerificationRequest();
        GooglePlayAppDetails appDetails = new GooglePlayAppDetails();
        appDetails.setService(MerchantServiceUtil.getService(callbackRequest.getPackageName()));
        appDetails.setOs(BaseConstants.ANDROID);
        appDetails.setPackageName(callbackRequest.getPackageName());
        GooglePlayProductDetails productDetails = new GooglePlayProductDetails();
        productDetails.setSkuId(callbackRequest.getSubscriptionId());
        request.setPaymentDetails(
                GooglePlayPaymentDetails.builder().purchaseToken(callbackRequest.getPurchaseToken()).notificationType(Integer.valueOf(callbackRequest.getNotificationType())).build());
        request.setAppDetails(appDetails);
        request.setProductDetails(productDetails);
        return request;
    }

    @Override
    public DecodedNotificationWrapper<GooglePlayCallbackRequest> isNotificationEligible (String requestPayload) {
        DeveloperNotification decodedData = mapAndDecodeData(requestPayload);
        if (Objects.isNull(decodedData.getSubscriptionNotification())) {
            if (Objects.nonNull(decodedData.getTestNotification())) {
                log.info("The notification is of test notification type with test data {}: ", decodedData.getTestNotification());
            } else if (Objects.nonNull(decodedData.getOneTimeProductNotification())) {
                log.info("The notification is for one time product purchase type with data {}: ", decodedData.getOneTimeProductNotification());
            }
            log.error("Ineligible realtime developer notification");
            return DecodedNotificationWrapper.<GooglePlayCallbackRequest>builder().decodedNotification(GooglePlayCallbackRequest.builder().build()).eligible(false).build();
        }
        GooglePlayCallbackRequest googlePlayCallbackRequest =
                GooglePlayCallbackRequest.builder().notificationType(decodedData.getSubscriptionNotification().getNotificationType()).packageName(decodedData.getPackageName())
                        .purchaseToken(decodedData.getSubscriptionNotification().getPurchaseToken()).subscriptionId(decodedData.getSubscriptionNotification().getSubscriptionId()).build();
        if (Objects.nonNull(googlePlayCallbackRequest) && NOTIFICATIONS_TYPE_ALLOWED.contains(googlePlayCallbackRequest.getNotificationType())) {
            final SubscriptionNotification subscriptionNotification = decodedData.getSubscriptionNotification();
            final String purchaseToken = subscriptionNotification.getPurchaseToken();
            Optional<ReceiptDetails> receiptDetailsOptional =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                            .findById(purchaseToken);
            boolean isEligible = receiptDetailsOptional.isPresent();
            return DecodedNotificationWrapper.<GooglePlayCallbackRequest>builder().decodedNotification(googlePlayCallbackRequest).eligible(isEligible).build();
        }
        return DecodedNotificationWrapper.<GooglePlayCallbackRequest>builder().decodedNotification(googlePlayCallbackRequest).eligible(false).build();
    }

    private DeveloperNotification mapAndDecodeData (String requestPayload) {
        try {
            GooglePlayNotificationMessage message = Utils.getData(requestPayload, GooglePlayNotificationMessage.class);
            byte[] valueDecoded = Base64.decodeBase64(message.getMessage().getData());
            return Utils.getData(new String(valueDecoded), DeveloperNotification.class);
        } catch (Exception e) {
            log.error("Exception occurred while decoding the data for the notification from Google Play: ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY030);
        }
    }

    @Override
    public PaymentEvent getPaymentEvent (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper, String productType) {
        String notificationType = wrapper.getDecodedNotification().getNotificationType();
        GooglePlayVerificationRequest request = new GooglePlayVerificationRequest();
        request.setPaymentDetails(GooglePlayPaymentDetails.builder().notificationType(Integer.valueOf(notificationType)).build());
        return MerchantServiceUtil.getGooglePlayEvent(request, wrapper.getDecodedNotification().getGooglePlayLatestReceiptResponse(), productType);
    }

    @Override
    public BaseResponse<?> verifyReceipt (LatestReceiptResponse latestReceiptResponse) {
        final Transaction transaction = TransactionContext.get();
        final GooglePlayBillingResponse.GooglePlayBillingData.GooglePlayBillingDataBuilder builder = GooglePlayBillingResponse.GooglePlayBillingData.builder();
        try {
            final GooglePlayLatestReceiptResponse response = (GooglePlayLatestReceiptResponse) latestReceiptResponse;
            fetchAndUpdateFromReceipt(transaction, response, null);
            MerchantServiceUtil.getUrl(transaction, latestReceiptResponse, builder);
            return BaseResponse.<GooglePlayBillingResponse>builder().body(GooglePlayBillingResponse.builder().data(builder.build()).build()).status(HttpStatus.OK).build();
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            MerchantServiceUtil.getUrl(transaction, latestReceiptResponse, builder);
            return BaseResponse.<GooglePlayBillingResponse>builder().body(GooglePlayBillingResponse.builder().message(e.getMessage()).success(false).data(builder.build()).build())
                    .status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void fetchAndUpdateFromReceipt (Transaction transaction, GooglePlayLatestReceiptResponse latestReceipt,
                                            GooglePlayReceiptDetails receiptDetails) {
        GooglePlayStatusCodes code = null;
        String productType = BaseConstants.PLAN;
        AbstractGooglePlayReceiptVerificationResponse abstractGooglePlayReceiptVerificationResponse = latestReceipt.getGooglePlayResponse();
        Integer notificationType = latestReceipt.getNotificationType();
        try {
            if (transaction.getType() == POINT_PURCHASE) {
                productType = BaseConstants.POINT;
                GooglePlayProductReceiptResponse productReceiptResponse = (GooglePlayProductReceiptResponse) abstractGooglePlayReceiptVerificationResponse;
                if (productReceiptResponse.getPurchaseState() == 0) {
                    transaction.setStatus(TransactionStatus.SUCCESS.name());
                    saveReceipt(transaction, latestReceipt, productType);
                } else if (productReceiptResponse.getPurchaseState() == 1) {
                    transaction.setStatus(TransactionStatus.CANCELLED.name());
                } else if (productReceiptResponse.getPurchaseState() == 2) {
                    transaction.setStatus(TransactionStatus.INPROGRESS.name());
                }
            } else {
                GooglePlaySubscriptionReceiptResponse latestResponse = (GooglePlaySubscriptionReceiptResponse) abstractGooglePlayReceiptVerificationResponse;
                if (Objects.isNull(latestResponse)) {
                    log.info("Latest receipt not found for uid: {} and transactionId {}", transaction.getUid(), transaction.getIdStr());
                    code = GooglePlayStatusCodes.GOOGLE_31018;
                    transaction.setStatus(TransactionStatus.FAILURE.name());
                } else {
                    AnalyticService.update(GOOGLE_PLAY_RECEIPT, gson.toJson(latestResponse));
                    Lock lock = wynkRedisLockService.getWynkRedisLock(latestReceipt.getPurchaseToken());

                    if (lock.tryLock(300, TimeUnit.MILLISECONDS)) {
                        if (Objects.isNull(receiptDetails) && !PURCHASE_NOTIFICATION_TYPE.equals(notificationType)) {
                            Optional<ReceiptDetails> receiptDetailsOptional =
                                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                                            .findById(latestReceipt.getPurchaseToken());
                            receiptDetails = receiptDetailsOptional.map(details -> (GooglePlayReceiptDetails) details).orElse(receiptDetails);
                        }
                        if (receiptDetails != null && (Objects.equals(latestReceipt.getNotificationType(), receiptDetails.getNotificationType())) &&
                                (Objects.equals(receiptDetails.getExpiry(), Long.parseLong(latestResponse.getExpiryTimeMillis())) ||
                                        Objects.equals(latestReceipt.getGooglePlayResponse().getOrderId(), receiptDetails.getSubscriptionId()))) {
                            log.info("Receipt is already processed for the UID {} and transactionId {}", transaction.getUid(), transaction.getIdStr());
                            code = GooglePlayStatusCodes.GOOGLE_31021;
                            transaction.setStatus(TransactionStatus.FAILUREALREADYSUBSCRIBED.name());
                        } else if (receiptDetails == null && !PURCHASE_NOTIFICATION_TYPE.equals(notificationType)) {
                            log.info("This type of notification is never processed and the notification is not of purchase type for UID {} and transaction Id {}", transaction.getUid(),
                                    transaction.getIdStr());
                            code = GooglePlayStatusCodes.GOOGLE_31006;
                            transaction.setStatus(TransactionStatus.FAILURE.name());
                        } else if ((receiptDetails == null && PURCHASE_NOTIFICATION_TYPE.equals(notificationType) && EnumSet.of(TransactionStatus.INPROGRESS).contains(transaction.getStatus()))) {
                            saveReceipt(transaction, latestReceipt, productType);
                            transaction.setStatus(TransactionStatus.SUCCESS.name());
                        } else {
                            final String purchaseToken = latestReceipt.getPurchaseToken();
                            if (Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_CANCELED.getNotificationTpe())) {
                                if (Long.parseLong(latestResponse.getExpiryTimeMillis()) >= receiptDetails.getExpiry()) {
                                    receiptDetails.setNotificationType(notificationType);
                                    receiptDetails.setRenew(transaction.getType().equals(RENEW));
                                    log.info("User has cancelled the plan from app store for uid: {}, purchaseToken :{} , transactionId: {}", transaction.getUid(), purchaseToken,
                                            transaction.getIdStr());
                                    code = GooglePlayStatusCodes.GOOGLE_31019;
                                    transaction.setStatus(TransactionStatus.CANCELLED.name());
                                } else {
                                    receiptDetails.setNotificationType(notificationType);
                                    receiptDetails.setRenew(transaction.getType().equals(RENEW));
                                    receiptDetails.setExpiry(Long.parseLong(latestResponse.getExpiryTimeMillis()));
                                    log.info("User has Unsubscribed the plan from app store for uid: {}, purchaseToken :{} , transactionId: {}", transaction.getUid(), purchaseToken,
                                            transaction.getIdStr());
                                    code = GooglePlayStatusCodes.GOOGLE_31019;
                                    transaction.setStatus(TransactionStatus.CANCELLED.name());
                                }
                            } else if (Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_RENEWED.getNotificationTpe())) {
                                if (receiptDetails.getExpiry() == Long.parseLong(latestResponse.getExpiryTimeMillis())) {
                                    code = GooglePlayStatusCodes.GOOGLE_31023;
                                    transaction.setStatus(TransactionStatus.FAILURE.name());
                                }
                                receiptDetails.setNotificationType(notificationType);
                                receiptDetails.setRenew(transaction.getType().equals(RENEW));
                                receiptDetails.setExpiry(Long.parseLong(latestResponse.getExpiryTimeMillis()));
                                transaction.setStatus(TransactionStatus.SUCCESS.name());
                            }
                            receiptDetails.setSubscriptionId(latestReceipt.getGooglePlayResponse().getOrderId());
                            receiptDetails.setPaymentTransactionId(transaction.getIdStr());
                            if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                                auditingListener.onBeforeSave(receiptDetails);
                                RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                                        .save(receiptDetails);
                            }
                        }
                        lock.unlock();
                    }
                }
            }
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            log.error(BaseLoggingMarkers.PAYMENT_ERROR, "fetchAndUpdateFromSource :: raised exception for uid : {} purchaseToken : {} ", transaction.getUid(), latestReceipt.getPurchaseToken(), e);
        } finally {
            if (transaction.getStatus() != TransactionStatus.SUCCESS && code != null) {
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(code.getErrorCode())).description(code.getErrorTitle()).build());
            }
        }
    }

    private void saveReceipt (Transaction transaction, GooglePlayLatestReceiptResponse latestReceiptResponse, String productType) {
        GooglePlayReceiptDetails.GooglePlayReceiptDetailsBuilder<?, ?> builder =
                GooglePlayReceiptDetails.builder().id(latestReceiptResponse.getPurchaseToken()).paymentTransactionId(transaction.getIdStr()).msisdn(transaction.getMsisdn()).uid(transaction.getUid())
                        .notificationType(latestReceiptResponse.getNotificationType())
                        .subscriptionId(latestReceiptResponse.getSubscriptionId()).packageName(latestReceiptResponse.getPackageName()).service(latestReceiptResponse.getService())
                        .skuId(latestReceiptResponse.getSkuId());

        if (BaseConstants.PLAN.equals(productType)) {
            GooglePlaySubscriptionReceiptResponse response = (GooglePlaySubscriptionReceiptResponse) latestReceiptResponse.getGooglePlayResponse();
            builder.planId(transaction.getPlanId());
            builder.expiry(Long.parseLong(response.getExpiryTimeMillis()));
            builder.renew(response.isAutoRenewing());
        }
        if (Objects.nonNull(transaction.getPlanId())) {

        } else if (Objects.nonNull(transaction.getItemId())) {
            builder.itemId(transaction.getItemId());
        }
        GooglePlayReceiptDetails googlePlayReceiptDetails = builder.build();
        auditingListener.onBeforeSave(googlePlayReceiptDetails);
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).save(googlePlayReceiptDetails);
    }

    @Override
    public LatestReceiptResponse getLatestReceiptResponse (IapVerificationRequestV2 iapVerificationRequest) {
        final GooglePlayVerificationRequest request = (GooglePlayVerificationRequest) iapVerificationRequest;
        String productType =
                (Objects.nonNull(request.getProductDetails().getType()) && (Objects.equals(request.getProductDetails().getType(), BaseConstants.POINT))) ? BaseConstants.POINT : BaseConstants.PLAN;
        if (Objects.isNull(request.getPaymentDetails()) || Objects.isNull(request.getProductDetails()) || Objects.isNull(request.getAppDetails()) ||
                (productType.equals(BaseConstants.POINT) && StringUtils.isEmpty(request.getProductDetails().getItemId()))) {
            throw new WynkRuntimeException(PaymentErrorType.PAY501);
        }
        AbstractGooglePlayReceiptVerificationResponse abstractGooglePlayReceiptVerificationResponse =
                googlePlayResponse(request.getPaymentDetails().getPurchaseToken(), request.getProductDetails().getSkuId(), request.getAppDetails().getPackageName(),
                        request.getAppDetails().getService(), productType);
        return mapGoogleResponseToReceiptResponse(abstractGooglePlayReceiptVerificationResponse, request, productType);
    }

    private AbstractGooglePlayReceiptVerificationResponse googlePlayResponse (String purchaseToken, String productId, String packageName, String service, String productType) {
        HttpHeaders headers = getHeaders(service);
        String key = getApiKey(service);
        try {
            if (BaseConstants.POINT.equals(productType)) {
                String url = baseUrl.concat(packageName).concat(productPurchase).concat(productId).concat(TOKEN).concat(purchaseToken).concat(API_KEY_PARAM).concat(key);
                return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), GooglePlayProductReceiptResponse.class).getBody();
            } else if (BaseConstants.PLAN.equals(productType)) {
                String url = baseUrl.concat(packageName).concat(subscriptionPurchase).concat(productId).concat(TOKEN).concat(purchaseToken).concat(API_KEY_PARAM).concat(key);
                return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), GooglePlaySubscriptionReceiptResponse.class).getBody();
            }
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE, "Exception while getting data from google Play API: {}", e.getMessage());
            throw new WynkRuntimeException(PaymentErrorType.PAY027);
        }
        return null;
    }

    private String getApiKey (String service) {
        if (SERVICE_MUSIC.equals(service)) {
            return musicKey;
        } else if (SERVICE_AIRTEL_TV.equals(service)) {
            return airtelTvKey;
        }
        throw new WynkRuntimeException("This service is not configured for API Key");
    }

    private HttpHeaders getHeaders (String service) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.AUTHORIZATION, AUTH_TOKEN_PREFIX.concat(googlePlayCacheService.get(service)));
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    private LatestReceiptResponse mapGoogleResponseToReceiptResponse (AbstractGooglePlayReceiptVerificationResponse abstractGooglePlayReceiptVerificationResponse,

                                                                      GooglePlayVerificationRequest request, String productType) {

        GooglePlayLatestReceiptResponse.GooglePlayLatestReceiptResponseBuilder<?, ?> builder =
                GooglePlayLatestReceiptResponse.builder()
                        .purchaseToken(request.getPaymentDetails().getPurchaseToken())
                        .extTxnId(request.getPaymentDetails().getPurchaseToken())
                        .notificationType(request.getPaymentDetails().getNotificationType())
                        .packageName(request.getAppDetails().getPackageName()).service(request.getAppDetails().getService())
                        .skuId(request.getProductDetails().getSkuId());
        if (BaseConstants.PLAN.equals(productType)) {
            GooglePlaySubscriptionReceiptResponse googlePlayReceiptResponse = (GooglePlaySubscriptionReceiptResponse) abstractGooglePlayReceiptVerificationResponse;
            boolean isFreeTrial = Objects.equals(googlePlayReceiptResponse.getPaymentState(), FREE_TRIAL_PAYMENT_STATE) || FREE_TRIAL_AMOUNT.equals(googlePlayReceiptResponse.getPriceAmountMicros());
            builder.freeTrial(isFreeTrial);
            builder.autoRenewal(googlePlayReceiptResponse.isAutoRenewing());
            builder.googlePlayResponse(googlePlayReceiptResponse);
            builder.couponCode(googlePlayReceiptResponse.getPromotionCode());
            builder.subscriptionId(googlePlayReceiptResponse.getOrderId());
            builder.planId(cachingService.getPlanFromSku(request.getProductDetails().getSkuId()).getId());
        } else {
            GooglePlayProductReceiptResponse googlePlayReceiptResponse = (GooglePlayProductReceiptResponse) abstractGooglePlayReceiptVerificationResponse;
            builder.googlePlayResponse(googlePlayReceiptResponse);
            builder.subscriptionId(googlePlayReceiptResponse.getOrderId());
            builder.itemId(request.getProductDetails().getItemId());
        }
        return builder.build();
    }

    @Override
    public void acknowledgeSubscription (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        if (GooglePlaySubscriptionAcknowledgementRequest.class.isAssignableFrom(abstractPaymentAcknowledgementRequest.getClass())) {
            reportSubscriptionPurchaseToGoogle((AbstractAcknowledgement) abstractPaymentAcknowledgementRequest);
        } else if (GooglePlayProductAcknowledgementRequest.class.isAssignableFrom(abstractPaymentAcknowledgementRequest.getClass())) {
            consumeProduct((AbstractAcknowledgement) abstractPaymentAcknowledgementRequest);
        } else {
            reportExternalTransactionSubscription((GooglePlayReportExternalTransactionRequest) abstractPaymentAcknowledgementRequest);
        }
    }

    @AnalyseTransaction(name = "subscriptionAcknowledgement")
    private void reportSubscriptionPurchaseToGoogle (AbstractAcknowledgement request) {
        AnalyticService.update(request);
        String url = baseUrl.concat(request.getAppDetails().getPackageName()).concat(subscriptionPurchase).concat(request.getProductDetails().getSkuId())
                .concat(TOKEN).concat(request.getPaymentDetails().getPurchaseToken()).concat(ACKNOWLEDGE).concat(API_KEY_PARAM)
                .concat(getApiKey(request.getAppDetails().getService()));
        GooglePlayAcknowledgeRequest body = GooglePlayAcknowledgeRequest.builder().developerPayload(request.getDeveloperPayload()).build();
        callGoogleApi(url, request, body, request.getAppDetails().getService());
    }

    // this acknowledgement is used for non-consumable products
    @AnalyseTransaction(name = "productAcknowledgement")
    private void reportProductPurchaseToGoogle (AbstractAcknowledgement request) {
        AnalyticService.update(request);
        String url = baseUrl.concat(request.getAppDetails().getPackageName()).concat(productPurchase).concat(request.getProductDetails().getSkuId()).concat(TOKEN)
                .concat(request.getPaymentDetails().getPurchaseToken())
                .concat(ACKNOWLEDGE).concat(API_KEY_PARAM)
                .concat(getApiKey(request.getAppDetails().getService()));
        GooglePlayAcknowledgeRequest body = GooglePlayAcknowledgeRequest.builder().developerPayload(request.getDeveloperPayload()).build();
        callGoogleApi(url, request, body, request.getAppDetails().getService());
    }

    @AnalyseTransaction(name = "productConsumption")
    private void consumeProduct (AbstractAcknowledgement request) {
        String url = baseUrl.concat(request.getAppDetails().getPackageName()).concat(productPurchase).concat(request.getProductDetails().getSkuId()).concat(TOKEN)
                .concat(request.getPaymentDetails().getPurchaseToken())
                .concat(CONSUME).concat(API_KEY_PARAM)
                .concat(getApiKey(request.getAppDetails().getService()));
        callGoogleApi(url, request, null, request.getAppDetails().getService());
        AnalyticService.update("txnId", request.getTxnId());
        AnalyticService.update("consumptionStatus", true);
    }

    @AnalyseTransaction(name = "externalTransactionAcknowledgement")
    public void reportExternalTransactionSubscription (GooglePlayReportExternalTransactionRequest request) {
        AnalyticService.update(request);
        final MsisdnOperatorDetails operatorDetails = userDetailsService.getOperatorDetails(request.getTransaction().getMsisdn());
        final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(request.getClientAlias());
        final String accessStateCode = userDetailsService.getAccessStateCode(operatorDetails, invoiceDetails.getDefaultGSTStateCode(), request.getPurchaseDetails());
        GSTStateCodes gstStateCodes = stateCodesCachingService.get(accessStateCode);

        String service = request.getPurchaseDetails().getAppDetails().getService();
        String packageName = MerchantServiceUtil.getPackageFromService(service);

        //Goggle requires dates in zulu format
        DateFormat zuluFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.mmm'Z'");
        zuluFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date transactionTime = new Date(request.getTransaction().getExitTime().getTimeInMillis());

        GooglePlayReportRequest.GooglePlayReportRequestBuilder builder = GooglePlayReportRequest.builder().transactionTime(zuluFormat.format(transactionTime)).userTaxAddress(
                ExternalTransactionAddress.builder().regionCode(stateCodesCachingService.get("+91").getCountryCode()).administrativeArea(gstStateCodes.getStateName().toUpperCase(Locale.ROOT))
                        .build());

        PaymentEvent paymentEvent = request.getTransaction().getType();
        setAmountBasedOnPaymentEvent(accessStateCode, gstStateCodes.getStateName(), stateCodesCachingService.get(DEFAULT_ACCESS_STATE_CODE).getStateName(), invoiceDetails.getGstPercentage(), builder,
                paymentEvent, request);

        GooglePlayReportRequest body = null;
        if (EnumSet.of(SUBSCRIBE, TRIAL_SUBSCRIPTION, MANDATE, FREE).contains(paymentEvent)) {
            body = builder.recurringTransaction(RecurringExternalTransaction.builder().externalTransactionToken(request.getExternalTransactionToken())
                    .externalSubscription(ExternalSubscription.builder().subscriptionType(SubscriptionType.RECURRING).build()).build()).build();
        } else if (EnumSet.of(PURCHASE, POINT_PURCHASE).contains(paymentEvent)) {
            body = builder.oneTimeTransaction(OneTimeExternalTransaction.builder().externalTransactionToken(request.getExternalTransactionToken()).build()).build();
        } else if (paymentEvent == RENEW) {
            body = builder.recurringTransaction(
                    RecurringExternalTransaction.builder().initialExternalTransactionId(request.getInitialTransactionId())
                            .externalSubscription(ExternalSubscription.builder().subscriptionType(SubscriptionType.RECURRING).build())
                            .build()).build();
        }
        HttpHeaders headers = getHeaders(service);
        String url = baseUrl.concat(packageName).concat(externalPurchase).concat(request.getTransaction().getIdStr()).concat(ETERNAL_TRANSACTION_API_KEY_PARAM).concat(getApiKey(service));
        try {
            ResponseEntity<GooglePlayReportResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), GooglePlayReportResponse.class);
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                GooglePlayReportResponse response = responseEntity.getBody();
                eventPublisher.publishEvent(GooglePlayReportEvent.builder().transactionId(response.getExternalTransactionId()).paymentEvent(paymentEvent.getValue())
                        .transactionState(response.getTransactionState()).createTime(response.getCreateTime())
                        .currentPreTaxAmount(response.getCurrentPreTaxAmount()).currentTaxAmount(response.getCurrentTaxAmount())
                        .service(MerchantServiceUtil.getService(response.getPackageName())).isTestPurchase(Objects.nonNull(response.getTestPurchase())).build());
            }
            log.info("Google acknowledged successfully for the external transaction Token {}", request.getExternalTransactionToken());
        } catch (Exception ex) {
            if (HttpClientErrorException.class.isAssignableFrom(ex.getClass()) && ((HttpClientErrorException) ex).getStatusCode() == HttpStatus.CONFLICT) {
                log.info("An external transaction with the provided id already exists.");
            } else {
                log.error(PaymentLoggingMarker.GOOGLE_PLAY_ACKNOWLEDGEMENT_FAILURE, "Exception occurred while acknowledging external transaction to google for the external transaction token {}: ",
                        request.getExternalTransactionToken());
                throw new WynkRuntimeException(PaymentErrorType.PAY050, ex);
            }
        }
    }

    private void callGoogleApi (String url, AbstractAcknowledgement request, GooglePlayAcknowledgeRequest body, String service) {
        HttpHeaders headers = getHeaders(service);
        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            log.info("Google api called successfully for the purchase with Purchase Token {}", request.getPaymentDetails().getPurchaseToken());
        } catch (Exception ex) {
            if (HttpClientErrorException.class.isAssignableFrom(ex.getClass()) && ((HttpClientErrorException) ex).getStatusCode() == HttpStatus.CONFLICT) {
                log.info("The operation could not be performed since the object was already in the process of being updated.");
            } else {
                log.error(PaymentLoggingMarker.GOOGLE_PLAY_ACKNOWLEDGEMENT_FAILURE, "Exception occurred while calling google api for the purchase with purchase token {}: ",
                        request.getPaymentDetails().getPurchaseToken());
                throw new WynkRuntimeException(PaymentErrorType.PAY029, ex.getMessage());
            }
        }
    }


    private void setAmountBasedOnPaymentEvent (String accessStateCode, String stateName, String defaultStateName, double gstPercentage, GooglePlayReportRequest.GooglePlayReportRequestBuilder builder,
                                               PaymentEvent paymentEvent,
                                               GooglePlayReportExternalTransactionRequest request) {
        if (EnumSet.of(TRIAL_SUBSCRIPTION, MANDATE, FREE).contains(paymentEvent)) {
            builder.originalPreTaxAmount(Price.builder().priceMicros("0").build()).originalTaxAmount(Price.builder().priceMicros("0").build());
        } else {
            //calculate amount to be sent to google
            final TaxableRequest taxableRequest = TaxableRequest.builder()
                    .consumerStateCode(accessStateCode).consumerStateName(stateName)
                    .supplierStateCode(DEFAULT_ACCESS_STATE_CODE).supplierStateName(defaultStateName)
                    .amount(request.getTransaction().getAmount()).gstPercentage(gstPercentage)
                    .build();
            TaxableResponse taxableResponse = taxManager.calculate(taxableRequest);
            //amount should be in 1/million of the currency base unit
            builder.originalPreTaxAmount(Price.builder().priceMicros(String.valueOf(Math.round(taxableResponse.getTaxableAmount() * 1000000))).build())
                    .originalTaxAmount(Price.builder().priceMicros(String.valueOf(Math.round(taxableResponse.getTaxAmount() * 1000000))).build());
        }
    }

    public void publishAsync (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        log.info("Trying to publish message on queue for google acknowledgement. ");
        if (abstractPaymentAcknowledgementRequest instanceof AbstractAcknowledgement) {
            AbstractAcknowledgement request = (AbstractAcknowledgement) abstractPaymentAcknowledgementRequest;
            PurchaseAcknowledgeMessageManager message = PurchaseAcknowledgeMessageManager.builder().paymentCode(request.getPaymentCode()).packageName(request.getAppDetails().getPackageName())
                    .service(request.getAppDetails().getService()).purchaseToken(request.getPaymentDetails()
                            .getPurchaseToken()).skuId(request.getProductDetails().getSkuId())
                    .developerPayload(request.getDeveloperPayload()).type(request.getType()).txnId(abstractPaymentAcknowledgementRequest.getTxnId()).build();
            try {
                sqsMessagePublisher.publishSQSMessage(message);
            } catch (Exception e) {
                log.error("Unable to publish acknowledge message on queue {}", e.getMessage());
            }
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status (AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        ChargingStatusResponse statusResponse = fetchChargingStatusFromGooglePlaySource(TransactionContext.get(), transactionStatusRequest.getExtTxnId());
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(statusResponse).build();
    }

    @SneakyThrows
    private ChargingStatusResponse fetchChargingStatusFromGooglePlaySource (Transaction transaction, String extTxnId) {
        if (transaction.getStatus() == TransactionStatus.FAILURE) {
            Optional<ReceiptDetails> receiptDetailsOptional =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                            .findById(extTxnId);
            if (receiptDetailsOptional.isPresent()) {
                GooglePlayReceiptDetails receiptDetails = (GooglePlayReceiptDetails) receiptDetailsOptional.get();
                fetchAndUpdateFromReceipt(transaction, getGooglePlayLatestReceiptResponse(transaction, receiptDetails), receiptDetails);
            } else {
                log.error(PAYMENT_RECONCILIATION_FAILURE, "unable to reconcile since receipt is not present for google Play {}", extTxnId);
            }
        }
        ChargingStatusResponse.ChargingStatusResponseBuilder<?, ?> responseBuilder =
                ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).tid(transaction.getIdStr());
        if (transaction.getPlanId() != null) {
            responseBuilder.planId(transaction.getPlanId());
            if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != POINT_PURCHASE) {
                responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId(), transaction.getMsisdn()));
            }
        } else {
            responseBuilder.itemId(transaction.getItemId());
        }
        return responseBuilder.build();
    }

    private GooglePlayLatestReceiptResponse getGooglePlayLatestReceiptResponse (Transaction transaction, GooglePlayReceiptDetails receiptDetails) {
        GooglePlaySubscriptionReceiptResponse googlePlayResponses = new GooglePlaySubscriptionReceiptResponse();
        googlePlayResponses.setAutoRenewing(receiptDetails.isRenew());
        googlePlayResponses.setOrderId(receiptDetails.getSubscriptionId());
        googlePlayResponses.setExpiryTimeMillis(String.valueOf(receiptDetails.getExpiry()));
        GooglePlayLatestReceiptResponse.GooglePlayLatestReceiptResponseBuilder<?, ?> builder =
                GooglePlayLatestReceiptResponse.builder().googlePlayResponse(googlePlayResponses).autoRenewal(receiptDetails.isRenew()).service(receiptDetails.getService())
                        .notificationType(receiptDetails.getNotificationType()).packageName(receiptDetails.getPackageName()).purchaseToken(receiptDetails.getId())
                        .subscriptionId(receiptDetails.getSubscriptionId()).skuId(receiptDetails.getSkuId()).extTxnId(receiptDetails.getId()).couponCode(transaction.getCoupon());
        if (transaction.getPlanId() != null) {
            builder.planId(transaction.getPlanId());
        } else if (StringUtils.isNotEmpty(transaction.getItemId())) {
            builder.itemId(transaction.getItemId());
        }
        return builder.build();
    }

    @Override
    public void verifyRequest (IapVerificationRequestV2Wrapper iapVerificationRequestV2Wrapper) {
        LatestReceiptResponse receipt = iapVerificationRequestV2Wrapper.getLatestReceiptResponse();
        GooglePlayLatestReceiptResponse googlePlayLatestReceipt = (GooglePlayLatestReceiptResponse) receipt;
        GooglePlaySubscriptionReceiptResponse response = null;
        GooglePlayVerificationRequest googlePlayRequest = (GooglePlayVerificationRequest) iapVerificationRequestV2Wrapper.getIapVerificationV2();
        if (googlePlayLatestReceipt.getPlanId() != 0) {
            response = (GooglePlaySubscriptionReceiptResponse) (googlePlayLatestReceipt.getGooglePlayResponse());
            if (Objects.nonNull(response)) {
                if (response.getLinkedPurchaseToken() != null) {
                    LatestReceiptResponse newLatestReceiptResponse = createResponseForLatestToken(response, googlePlayLatestReceipt);
                    PaymentManager paymentManager = BeanLocatorFactory.getBean(PaymentManager.class);
                    paymentManager.doVerifyIap(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString(),
                            IapVerificationRequestV2Wrapper.builder().iapVerificationV2(iapVerificationRequestV2Wrapper.getIapVerificationV2()).latestReceiptResponse(newLatestReceiptResponse)
                                    .build());
                } else if (response.getCancelReason() != null) {
                    log.error("The receipt is for cancelled Subscription.");
                    googlePlayLatestReceipt.setNotificationType(3);
                    GooglePlayPaymentDetails paymentDetails = googlePlayRequest.getPaymentDetails();
                    paymentDetails.setNotificationType(3);
                    googlePlayRequest.setPaymentDetails(paymentDetails);
                }
            }
        } else {
            GooglePlayProductReceiptResponse productReceiptResponse = (GooglePlayProductReceiptResponse) googlePlayLatestReceipt.getGooglePlayResponse();
            if (productReceiptResponse.getPurchaseState() == 1) {
                log.error("The receipt is for cancelled Product purchase.");
                googlePlayLatestReceipt.setNotificationType(3);
                GooglePlayPaymentDetails paymentDetails = googlePlayRequest.getPaymentDetails();
                paymentDetails.setNotificationType(3);
                googlePlayRequest.setPaymentDetails(paymentDetails);
            }
        }

    }

    private LatestReceiptResponse createResponseForLatestToken (GooglePlaySubscriptionReceiptResponse googlePlayReceiptResponse, GooglePlayLatestReceiptResponse googlePlayLatestReceiptResponse) {
        googlePlayReceiptResponse.setExpiryTimeMillis(String.valueOf(System.currentTimeMillis()));
        boolean isFreeTrial = Objects.equals(googlePlayReceiptResponse.getPaymentState(), FREE_TRIAL_PAYMENT_STATE) || FREE_TRIAL_AMOUNT.equals(googlePlayReceiptResponse.getPriceAmountMicros());
        return GooglePlayLatestReceiptResponse.builder().freeTrial(isFreeTrial).googlePlayResponse(googlePlayReceiptResponse).planId(googlePlayLatestReceiptResponse.getPlanId())
                .purchaseToken(googlePlayReceiptResponse.getLinkedPurchaseToken()) //purchase token should be linked purchase token
                .extTxnId(googlePlayReceiptResponse.getLinkedPurchaseToken()).couponCode(googlePlayLatestReceiptResponse.getCouponCode())
                .notificationType(GooglePlayNotificationType.SUBSCRIPTION_CANCELED.getNotificationTpe()) //add notification type to Cancelled
                .subscriptionId(googlePlayLatestReceiptResponse.getSubscriptionId()).packageName(googlePlayLatestReceiptResponse.getPackageName()).service(googlePlayLatestReceiptResponse.getService())
                .autoRenewal(false) //if cancelled means autoRenewal should be false
                .build();
    }

    @Override
    public WynkResponseEntity<Void> doRenewal (PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        final Transaction transaction = TransactionContext.get();
        log.info("Auto renewal request received for google play-------->\n");
        try {
            final GooglePlayReceiptDetails receiptDetails =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                            .findByPaymentTransactionId(paymentRenewalChargingRequest.getId());
            if (Objects.nonNull(receiptDetails)) {
                String productType = receiptDetails.getPlanId() == 0 ? BaseConstants.POINT : BaseConstants.PLAN;
                final AbstractGooglePlayReceiptVerificationResponse
                        abstractGooglePlayReceiptVerificationResponse =
                        googlePlayResponse(receiptDetails.getId(), receiptDetails.getSkuId(), receiptDetails.getPackageName(), receiptDetails.getService(), productType);
                if (abstractGooglePlayReceiptVerificationResponse.getOrderId().equals(receiptDetails.getSubscriptionId())) {
                    AnalyticService.update(GOOGLE_PLAY_ORDER_ID, abstractGooglePlayReceiptVerificationResponse.getOrderId());
                    throw new WynkRuntimeException(PaymentErrorType.PLAY005);
                }
                GooglePlayAppDetails appDetails = new GooglePlayAppDetails();
                appDetails.setService(MerchantServiceUtil.getService(receiptDetails.getPackageName()));
                appDetails.setOs(BaseConstants.ANDROID);
                appDetails.setPackageName(receiptDetails.getPackageName());
                GooglePlayProductDetails productDetails = new GooglePlayProductDetails();
                productDetails.setSkuId(receiptDetails.getSkuId());
                GooglePlayVerificationRequest request = new GooglePlayVerificationRequest();
                request.setProductDetails(productDetails);
                request.setPaymentDetails(GooglePlayPaymentDetails.builder().purchaseToken(receiptDetails.getId()).notificationType(2).build());
                request.setAppDetails(appDetails);

                final LatestReceiptResponse latestReceiptResponse = mapGoogleResponseToReceiptResponse(abstractGooglePlayReceiptVerificationResponse, request, productType);
                fetchAndUpdateFromReceipt(transaction, (GooglePlayLatestReceiptResponse) latestReceiptResponse, receiptDetails);
                return WynkResponseEntity.<Void>builder().success(true).build();
            }
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            return WynkResponseEntity.<Void>builder().success(false).build();
        } catch (Exception e) {
            if (WynkRuntimeException.class.isAssignableFrom(e.getClass())) {
                final WynkRuntimeException exception = (WynkRuntimeException) e;
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(exception.getErrorCode())).description(exception.getErrorTitle()).build());
            }
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException(e);
        }
    }

    @Override
    public boolean supportsRenewalReconciliation () {
        return false;
    }

    @Override
    public void cancelSubscription (String uid, String transactionId) {
        List<ReceiptDetails> receiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                .findByUid(uid);
        Optional<ReceiptDetails> filteredReceipt = receiptDetails.stream().filter(receiptDetails1 -> receiptDetails1.getPaymentTransactionId().equals(transactionId)).findAny();
        GooglePlayReceiptDetails gPlayReceipt = filteredReceipt.map(details -> (GooglePlayReceiptDetails) details).orElseGet(() -> (GooglePlayReceiptDetails) receiptDetails.get(0));
        String url = baseUrl.concat(gPlayReceipt.getPackageName()).concat(subscriptionPurchase).concat(gPlayReceipt.getSkuId())
                .concat(TOKEN).concat(gPlayReceipt.getId()).concat(CANCEL).concat(API_KEY_PARAM)
                .concat(getApiKey(gPlayReceipt.getService()));
        HttpHeaders headers = getHeaders(gPlayReceipt.getService());
        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(null, headers), String.class);
        } catch (Exception ex) {
            throw new WynkRuntimeException("Exception occurred while calling google api for subscription Cancellation", ex);
        }
    }
}

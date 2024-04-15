package in.wynk.payment.service.impl;

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
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.service.GSTStateCodesCachingService;
import in.wynk.payment.core.service.InvoiceDetailsCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.gpbs.*;
import in.wynk.payment.dto.gpbs.acknowledge.queue.SubscriptionAcknowledgeMessageManager;
import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlayReportExternalTransactionRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlaySubscriptionAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.notification.request.DeveloperNotification;
import in.wynk.payment.dto.gpbs.notification.request.GooglePlayNotificationMessage;
import in.wynk.payment.dto.gpbs.notification.request.SubscriptionNotification;
import in.wynk.payment.dto.gpbs.request.*;
import in.wynk.payment.dto.gpbs.request.externalTransaction.ExternalSubscription;
import in.wynk.payment.dto.gpbs.request.externalTransaction.ExternalTransactionAddress;
import in.wynk.payment.dto.gpbs.request.externalTransaction.OneTimeExternalTransaction;
import in.wynk.payment.dto.gpbs.request.externalTransaction.RecurringExternalTransaction;
import in.wynk.payment.dto.gpbs.response.externalTransaction.GooglePlayReportResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayReceiptResponse;
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
        IReceiptDetailService<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>, GooglePlayCallbackRequest>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest> {
    @Value("${payment.googlePlay.baseUrl}")
    private String baseUrl;
    @Value("${payment.googlePlay.purchaseUrl}")
    private String purchaseUrl;
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

        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        GooglePlayLatestReceiptResponse receiptInfo = mapping.getMessage().getFirst();
        final long expireTimestamp = NumberUtils.toLong(receiptInfo.getGooglePlayResponse().getExpiryTimeMillis());
        if (expireTimestamp > System.currentTimeMillis() || (transaction.getType() == CANCELLED && expireTimestamp <= System.currentTimeMillis())) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
        }
        transaction.setStatus(finalTransactionStatus.name());
        saveReceipt(transaction, receiptInfo);
    }

    @Override
    public UserPlanMapping<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>> getUserPlanMapping (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper) {
        GooglePlayCallbackRequest callbackRequest = wrapper.getDecodedNotification();
        //verify the receipt from server and then add txnType to mapping
        GooglePlayReceiptResponse googlePlayReceiptResponse =
                googlePlayResponse(callbackRequest.getPurchaseToken(), callbackRequest.getSubscriptionId(), callbackRequest.getPackageName(),
                        MerchantServiceUtil.getService(callbackRequest.getPackageName()));
        LatestReceiptResponse latestReceiptResponse = mapGoogleResponseToReceiptResponse(googlePlayReceiptResponse, createRequest(callbackRequest));
        final GooglePlayLatestReceiptResponse latestReceipt = (GooglePlayLatestReceiptResponse) latestReceiptResponse;
        //set the latest response to be used while deciding payment event
        wrapper.getDecodedNotification().setGooglePlayLatestReceiptResponse(latestReceipt);
        Optional<ReceiptDetails> optionalReceiptDetails =
                RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                        .findById(latestReceipt.getPurchaseToken());
        AnalyticService.update(GOOGLE_PLAY_RECEIPT, gson.toJson(latestReceipt));
        if (optionalReceiptDetails.isPresent()) {
            ReceiptDetails receiptDetails = optionalReceiptDetails.get();
            GooglePlayReceiptDetails googlePlayReceiptDetails = (GooglePlayReceiptDetails) receiptDetails;
            if ((googlePlayReceiptDetails.getSubscriptionId().equals(googlePlayReceiptResponse.getOrderId())) ||
                    (Objects.equals(String.valueOf(receiptDetails.getExpiry()), latestReceipt.getGooglePlayResponse().getExpiryTimeMillis()) &&
                            Objects.equals(receiptDetails.getNotificationType(), latestReceipt.getNotificationType()))) {
                log.info("Notification is already processed for the purchase token {}", latestReceipt.getPurchaseToken());
                return null;
            }
            PlanDTO planDTO = cachingService.getPlanFromSku(googlePlayReceiptDetails.getSkuId());
            boolean isFreeTrial = Objects.equals(googlePlayReceiptResponse.getPaymentState(), FREE_TRIAL_PAYMENT_STATE) || FREE_TRIAL_AMOUNT.equals(googlePlayReceiptResponse.getPriceAmountMicros());
            //if free trial plan applied, perform events on that plan
            if (isFreeTrial) {
                if (planDTO.getLinkedFreePlanId() != -1) {
                    return UserPlanMapping.<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>>builder().planId(planDTO.getLinkedFreePlanId()).msisdn(receiptDetails.getMsisdn())
                            .uid(receiptDetails.getUid())
                            .linkedTransactionId(receiptDetails.getPaymentTransactionId())
                            .message(Pair.of(latestReceipt, receiptDetails)).build();
                } else {
                    log.error("No Free Trial mapping present for planId {}", planDTO.getId());
                    throw new WynkRuntimeException(PaymentErrorType.PAY033);
                }
            }
            return UserPlanMapping.<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>>builder().planId(planDTO.getId()).msisdn(receiptDetails.getMsisdn()).uid(receiptDetails.getUid())
                    .linkedTransactionId(receiptDetails.getPaymentTransactionId())
                    .message(Pair.of(latestReceipt, receiptDetails)).build();
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
    public PaymentEvent getPaymentEvent (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper) {
        String notificationType = wrapper.getDecodedNotification().getNotificationType();
        GooglePlayVerificationRequest request = new GooglePlayVerificationRequest();
        request.setPaymentDetails(GooglePlayPaymentDetails.builder().notificationType(Integer.valueOf(notificationType)).build());
        return MerchantServiceUtil.getGooglePlayEvent(request, wrapper.getDecodedNotification().getGooglePlayLatestReceiptResponse());
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
        GooglePlayReceiptResponse latestResponse = latestReceipt.getGooglePlayResponse();
        Integer notificationType = latestReceipt.getNotificationType();
        try {
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
                        saveReceipt(transaction, latestReceipt);
                        transaction.setStatus(TransactionStatus.SUCCESS.name());
                    } else {
                        final String purchaseToken = latestReceipt.getPurchaseToken();
                        if (Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_CANCELED.getNotificationTpe())) {
                            if (Long.parseLong(latestResponse.getExpiryTimeMillis()) >= receiptDetails.getExpiry()) {
                                receiptDetails.setNotificationType(notificationType);
                                receiptDetails.setRenew(transaction.getType().equals(RENEW));
                                log.info("User has cancelled the plan from app store for uid: {}, purchaseToken :{} , transactionId: {}", transaction.getUid(), purchaseToken, transaction.getIdStr());
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
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            log.error(BaseLoggingMarkers.PAYMENT_ERROR, "fetchAndUpdateFromSource :: raised exception for uid : {} purchaseToken : {} ", transaction.getUid(), latestReceipt.getPurchaseToken(), e);
        } finally {
            if (transaction.getStatus() != TransactionStatus.SUCCESS && code != null) {
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(code.getErrorCode())).description(code.getErrorTitle()).build());
            }
        }
    }

    private void saveReceipt (Transaction transaction, GooglePlayLatestReceiptResponse latestReceiptResponse) {
        GooglePlayReceiptDetails.GooglePlayReceiptDetailsBuilder<?, ?> builder =
                GooglePlayReceiptDetails.builder().id(latestReceiptResponse.getPurchaseToken()).paymentTransactionId(transaction.getIdStr()).msisdn(transaction.getMsisdn()).uid(transaction.getUid())
                        .expiry(Long.parseLong(latestReceiptResponse.getGooglePlayResponse().getExpiryTimeMillis())).notificationType(latestReceiptResponse.getNotificationType())
                        .subscriptionId(latestReceiptResponse.getSubscriptionId()).packageName(latestReceiptResponse.getPackageName()).service(latestReceiptResponse.getService())
                        .skuId(latestReceiptResponse.getSkuId()).renew(latestReceiptResponse.getGooglePlayResponse().isAutoRenewing());
        if (Objects.nonNull(transaction.getPlanId())) {
            builder.planId(transaction.getPlanId());
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
        if (Objects.isNull(request.getPaymentDetails()) || Objects.isNull(request.getProductDetails()) || Objects.isNull(request.getAppDetails())) {
            throw new WynkRuntimeException(PaymentErrorType.PAY501);
        }
        GooglePlayReceiptResponse googlePlayReceiptResponse =
                googlePlayResponse(request.getPaymentDetails().getPurchaseToken(), request.getProductDetails().getSkuId(), request.getAppDetails().getPackageName(),
                        request.getAppDetails().getService());
        return mapGoogleResponseToReceiptResponse(googlePlayReceiptResponse, request);
    }

    private GooglePlayReceiptResponse googlePlayResponse (String purchaseToken, String productId, String packageName, String service) {
        HttpHeaders headers = getHeaders(service);
        String key = getApiKey(service);
        try {
            String url = baseUrl.concat(packageName).concat(purchaseUrl).concat(productId).concat(TOKEN).concat(purchaseToken).concat(API_KEY_PARAM).concat(key);
            return getPlayStoreResponse(url, headers).getBody();
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE, "Exception while getting data from google Play API: {}", e.getMessage());
            throw new WynkRuntimeException(PaymentErrorType.PAY027);
        }
    }

    private String getApiKey (String service) {
        if (SERVICE_MUSIC.equals(service)) {
            return musicKey;
        } else if (SERVICE_AIRTEL_TV.equals(service)) {
            return airtelTvKey;
        }
        throw new WynkRuntimeException("This service is not configured for API Key");
    }

    private ResponseEntity<GooglePlayReceiptResponse> getPlayStoreResponse (String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), GooglePlayReceiptResponse.class);
    }

    private HttpHeaders getHeaders (String service) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.AUTHORIZATION, AUTH_TOKEN_PREFIX.concat(googlePlayCacheService.get(service)));
        return headers;
    }

    private LatestReceiptResponse mapGoogleResponseToReceiptResponse (GooglePlayReceiptResponse googlePlayReceiptResponse, GooglePlayVerificationRequest request) {
        boolean isFreeTrial = Objects.equals(googlePlayReceiptResponse.getPaymentState(), FREE_TRIAL_PAYMENT_STATE) || FREE_TRIAL_AMOUNT.equals(googlePlayReceiptResponse.getPriceAmountMicros());
        GooglePlayLatestReceiptResponse.GooglePlayLatestReceiptResponseBuilder<?, ?> builder =
                GooglePlayLatestReceiptResponse.builder().freeTrial(isFreeTrial).autoRenewal(googlePlayReceiptResponse.isAutoRenewing()).googlePlayResponse(googlePlayReceiptResponse)
                        .purchaseToken(request.getPaymentDetails().getPurchaseToken())
                        .extTxnId(request.getPaymentDetails().getPurchaseToken()).couponCode(googlePlayReceiptResponse.getPromotionCode())
                        .notificationType(request.getPaymentDetails().getNotificationType())
                        .subscriptionId(googlePlayReceiptResponse.getOrderId()).packageName(request.getAppDetails().getPackageName()).service(request.getAppDetails().getService())
                        .skuId(request.getProductDetails().getSkuId());
        if (StringUtils.isNotEmpty(request.getProductDetails().getPlanId())) {
            builder.planId(cachingService.getPlanFromSku(request.getProductDetails().getSkuId()).getId());
        } else if (StringUtils.isNotEmpty(request.getProductDetails().getItemId())) {
            builder.itemId(request.getProductDetails().getItemId());
        }
        return builder.build();
    }

    @Override
    public void acknowledgeSubscription (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        if (GooglePlaySubscriptionAcknowledgementRequest.class.isAssignableFrom(abstractPaymentAcknowledgementRequest.getClass())) {
            GooglePlaySubscriptionAcknowledgementRequest request = (GooglePlaySubscriptionAcknowledgementRequest) abstractPaymentAcknowledgementRequest;
            HttpHeaders headers = getHeaders(request.getAppDetails().getService());
            GooglePlayAcknowledgeRequest body = GooglePlayAcknowledgeRequest.builder().developerPayload(request.getDeveloperPayload()).build();
            String url =
                    baseUrl.concat(request.getAppDetails().getPackageName()).concat(purchaseUrl).concat(request.getProductDetails().getSkuId())
                            .concat(TOKEN).concat(request.getPaymentDetails().getPurchaseToken()).concat(ACKNOWLEDGE).concat(API_KEY_PARAM)
                            .concat(getApiKey(request.getAppDetails().getService()));
            try {
                restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), GooglePlayReceiptResponse.class);
                log.info("Google acknowledged successfully for the purchase with Purchase Token {}", request.getPaymentDetails().getPurchaseToken());
            } catch (Exception e) {
                log.error(PaymentLoggingMarker.GOOGLE_PLAY_ACKNOWLEDGEMENT_FAILURE, "Exception occurred while acknowledging google for the purchase with purchase token {}: ",
                        request.getPaymentDetails().getPurchaseToken());
                throw new WynkRuntimeException(PaymentErrorType.PAY029, e);
            }
        } else {
            reportExternalTransactionSubscription((GooglePlayReportExternalTransactionRequest) abstractPaymentAcknowledgementRequest);
        }

    }

    public void reportExternalTransactionSubscription (GooglePlayReportExternalTransactionRequest request) {
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
        } else if (paymentEvent == PURCHASE) {
            body = builder.oneTimeTransaction(OneTimeExternalTransaction.builder().externalTransactionToken(request.getExternalTransactionToken()).build()).build();
        } else if (paymentEvent == RENEW) {
            Optional<PaymentRenewal> renewalOptional =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class)
                            .findById(request.getTransaction().getIdStr());
            if (renewalOptional.isPresent()) {
                body = builder.recurringTransaction(
                        RecurringExternalTransaction.builder().initialExternalTransactionId(renewalOptional.get().getInitialTransactionId())
                                .externalSubscription(ExternalSubscription.builder().subscriptionType(SubscriptionType.RECURRING).build())
                                .build()).build();
            } else {
                throw new WynkRuntimeException("Unable to report renewal transactions to google");
            }

        }
        HttpHeaders headers = getHeaders(service);
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        String url = baseUrl.concat(packageName).concat(EXTERNAL_TRANSACTION_PARAM).concat(request.getTransaction().getIdStr()).concat(ETERNAL_TRANSACTION_API_KEY_PARAM).concat(getApiKey(service));
        try {
            ResponseEntity<GooglePlayReportResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), GooglePlayReportResponse.class);
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                GooglePlayReportResponse response = responseEntity.getBody();
                eventPublisher.publishEvent(GooglePlayReportEvent.builder().transactionId(response.getExternalTransactionId())
                        .transactionState(response.getTransactionState()).createTime(response.getCreateTime())
                        .currentPreTaxAmount(response.getCurrentPreTaxAmount()).currentTaxAmount(response.getCurrentTaxAmount())
                        .service(MerchantServiceUtil.getService(response.getPackageName())).isTestPurchase(Objects.nonNull(response.getTestPurchase())).build());
            }
            log.info("Google acknowledged successfully for the external transaction Token {}", request.getExternalTransactionToken());
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                log.info("An external transaction with the provided id already exists.");
            } else {
                throw new WynkRuntimeException(PaymentErrorType.PAY050, ex);
            }
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.GOOGLE_PLAY_ACKNOWLEDGEMENT_FAILURE, "Exception occurred while acknowledging external transaction to google for the external transaction token {}: ",
                    request.getExternalTransactionToken());
            throw new WynkRuntimeException(PaymentErrorType.PAY050, e);
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
        GooglePlaySubscriptionAcknowledgementRequest request = (GooglePlaySubscriptionAcknowledgementRequest) abstractPaymentAcknowledgementRequest;
        SubscriptionAcknowledgeMessageManager
                message = SubscriptionAcknowledgeMessageManager.builder().paymentCode(request.getPaymentCode()).packageName(request.getAppDetails().getPackageName())
                .service(request.getAppDetails().getService()).purchaseToken(request.getPaymentDetails()
                        .getPurchaseToken()).skuId(request.getProductDetails().getSkuId())
                .developerPayload(request.getDeveloperPayload()).build();
        try {
            sqsMessagePublisher.publishSQSMessage(message);
        } catch (Exception e) {
            log.error("Unable to publish acknowledge message on queue {}", e.getMessage());
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
        GooglePlayReceiptResponse googlePlayResponses = new GooglePlayReceiptResponse();
        googlePlayResponses.setAutoRenewing(receiptDetails.isRenew());
        googlePlayResponses.setOrderId(receiptDetails.getSubscriptionId());
        googlePlayResponses.setExpiryTimeMillis(String.valueOf(receiptDetails.getExpiry()));
        return GooglePlayLatestReceiptResponse.builder().googlePlayResponse(googlePlayResponses).autoRenewal(receiptDetails.isRenew()).service(receiptDetails.getService())
                .notificationType(receiptDetails.getNotificationType()).planId(transaction.getPlanId()).packageName(receiptDetails.getPackageName()).purchaseToken(receiptDetails.getId())
                .subscriptionId(receiptDetails.getSubscriptionId()).skuId(receiptDetails.getSkuId()).extTxnId(receiptDetails.getId()).couponCode(transaction.getCoupon()).build();
    }

    @Override
    public void verifyRequest (IapVerificationRequestV2Wrapper iapVerificationRequestV2Wrapper) {
        LatestReceiptResponse receipt = iapVerificationRequestV2Wrapper.getLatestReceiptResponse();
        GooglePlayLatestReceiptResponse googlePlayLatestReceipt = (GooglePlayLatestReceiptResponse) receipt;
        GooglePlayVerificationRequest googlePlayRequest = (GooglePlayVerificationRequest) iapVerificationRequestV2Wrapper.getIapVerificationV2();
        if (Objects.nonNull(receipt)) {
            if (googlePlayLatestReceipt.getGooglePlayResponse().getLinkedPurchaseToken() != null) {
                GooglePlayReceiptResponse googlePlayReceiptResponse = googlePlayLatestReceipt.getGooglePlayResponse();
                LatestReceiptResponse newLatestReceiptResponse = createResponseForLatestToken(googlePlayReceiptResponse, googlePlayLatestReceipt);
                PaymentManager paymentManager = BeanLocatorFactory.getBean(PaymentManager.class);
                paymentManager.doVerifyIap(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString(),
                        IapVerificationRequestV2Wrapper.builder().iapVerificationV2(iapVerificationRequestV2Wrapper.getIapVerificationV2()).latestReceiptResponse(newLatestReceiptResponse).build());
            } else if (Objects.nonNull(receipt) && googlePlayLatestReceipt.getGooglePlayResponse().getCancelReason() != null) {
                log.error("The receipt is for cancelled Subscription.");
                googlePlayLatestReceipt.setNotificationType(3);
                GooglePlayPaymentDetails paymentDetails = googlePlayRequest.getPaymentDetails();
                paymentDetails.setNotificationType(3);
                googlePlayRequest.setPaymentDetails(paymentDetails);
            }
        }
    }

    private LatestReceiptResponse createResponseForLatestToken (GooglePlayReceiptResponse googlePlayReceiptResponse, GooglePlayLatestReceiptResponse googlePlayLatestReceiptResponse) {
        googlePlayReceiptResponse.setExpiryTimeMillis(String.valueOf(System.currentTimeMillis()));
        boolean isFreeTrial = Objects.equals(googlePlayReceiptResponse.getPaymentState(), FREE_TRIAL_PAYMENT_STATE) || FREE_TRIAL_AMOUNT.equals(googlePlayReceiptResponse.getPriceAmountMicros());
        return GooglePlayLatestReceiptResponse.builder().freeTrial(isFreeTrial).googlePlayResponse(googlePlayReceiptResponse).planId(googlePlayLatestReceiptResponse.getPlanId())
                .purchaseToken(googlePlayLatestReceiptResponse.getGooglePlayResponse().getLinkedPurchaseToken()) //purchase token should be linked purchase token
                .extTxnId(googlePlayLatestReceiptResponse.getGooglePlayResponse().getLinkedPurchaseToken()).couponCode(googlePlayLatestReceiptResponse.getCouponCode())
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
                final GooglePlayReceiptResponse googlePlayReceipt = googlePlayResponse(receiptDetails.getId(), receiptDetails.getSkuId(), receiptDetails.getPackageName(), receiptDetails.getService());
                if (googlePlayReceipt.getOrderId().equals(receiptDetails.getSubscriptionId())) {
                    AnalyticService.update(GOOGLE_PLAY_ORDER_ID, googlePlayReceipt.getOrderId());
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

                final LatestReceiptResponse latestReceiptResponse = mapGoogleResponseToReceiptResponse(googlePlayReceipt, request);
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
}

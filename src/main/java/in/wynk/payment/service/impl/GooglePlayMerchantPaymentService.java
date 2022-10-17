package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
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
import in.wynk.payment.core.dao.entity.GooglePlayReceiptDetails;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.gpbs.*;
import in.wynk.payment.dto.gpbs.notification.request.DeveloperNotification;
import in.wynk.payment.dto.gpbs.notification.request.GooglePlayNotificationMessage;
import in.wynk.payment.dto.gpbs.notification.request.SubscriptionNotification;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayReceiptResponse;
import in.wynk.payment.dto.gpbs.request.*;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.internal.resources.Marker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

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
    @Value("${payment.googlePlay.rajTv.key}")
    private String rajTvKey;
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


    public GooglePlayMerchantPaymentService (@Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, Gson gson,
                                             ApplicationEventPublisher eventPublisher, WynkRedisLockService wynkRedisLockService, IErrorCodesCacheService errorCodesCacheServiceImpl,
                                             GooglePlayCacheService googlePlayCacheService, PaymentCachingService cachingService,
                                             ISqsManagerService sqsMessagePublisher) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.gson = gson;
        this.restTemplate = restTemplate;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.wynkRedisLockService = wynkRedisLockService;
        this.googlePlayCacheService = googlePlayCacheService;
        this.sqsMessagePublisher = sqsMessagePublisher;
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
        //GooglePlayReceiptDetails receiptDetails = (GooglePlayReceiptDetails) mapping.getMessage().getSecond();
        final long expireTimestamp = NumberUtils.toLong(receiptInfo.getGooglePlayResponse().getExpiryTimeMillis());
        if (expireTimestamp > System.currentTimeMillis() || (transaction.getType() == PaymentEvent.CANCELLED && expireTimestamp <= System.currentTimeMillis())) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
        }
        transaction.setStatus(finalTransactionStatus.name());
        saveReceipt(transaction.getUid(),transaction.getMsisdn(), transaction.getPlanId(), transaction.getIdStr(), receiptInfo);

    }

    @Override
    public UserPlanMapping<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>> getUserPlanMapping (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper) {
        GooglePlayCallbackRequest callbackRequest = wrapper.getDecodedNotification();
        if (NOTIFICATIONS_TYPE_ALLOWED.contains(callbackRequest.getNotificationType()) && callbackRequest.getPurchaseToken() != null) {
            //verify the receipt from server and then add txnType to mapping
            GooglePlayVerificationRequest request = new GooglePlayVerificationRequest();
            PlanDTO planDTO = cachingService.getPlanFromSku(callbackRequest.getSubscriptionId());

            request.setPaymentDetails(
                    GooglePlayPaymentDetails.builder().purchaseToken(callbackRequest.getPurchaseToken()).notificationType(Integer.valueOf(callbackRequest.getNotificationType())).build());
            request.setAppDetails(GooglePlayAppDetails.builder().service(MerchantServiceUtil.getService(callbackRequest.getPackageName())).os(BaseConstants.ANDROID).build());
            request.setProductDetails(GooglePlayProductDetails.builder().planId(String.valueOf(planDTO.getId())).build());

            LatestReceiptResponse latestReceiptResponse = getLatestReceiptResponse(request);

            if (Objects.nonNull(latestReceiptResponse)) {
                final GooglePlayLatestReceiptResponse latestReceipt = (GooglePlayLatestReceiptResponse) latestReceiptResponse;
               //set the latest response to be used while deciding payment event
                wrapper.getDecodedNotification().setGooglePlayLatestReceiptResponse(latestReceipt);
                Optional<ReceiptDetails> optionalReceiptDetails =
                        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                                .findById(latestReceipt.getPurchaseToken());
                AnalyticService.update(GOOGLE_PLAY_RECEIPT, gson.toJson(latestReceipt));
                if (optionalReceiptDetails.isPresent()) {
                    ReceiptDetails details = optionalReceiptDetails.get();
                    return UserPlanMapping.<Pair<GooglePlayLatestReceiptResponse, ReceiptDetails>>builder().planId(planDTO.getId()).msisdn(details.getMsisdn()).uid(details.getUid())
                            .message(Pair.of(latestReceipt, details)).build();
                }
            }
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid Request");
    }
    
    //handle notification
    @Override
    public DecodedNotificationWrapper<GooglePlayCallbackRequest> isNotificationEligible (String requestPayload) {
        GooglePlayNotificationMessage message = new GooglePlayNotificationMessage();
        DeveloperNotification decodedData = decodeData(message.getMessage().getData());
        GooglePlayCallbackRequest googlePlayCallbackRequest =
                GooglePlayCallbackRequest.builder().notificationType(decodedData.getSubscriptionNotification().getNotificationType()).packageName(decodedData.getPackageName())
                        .purchaseToken(decodedData.getSubscriptionNotification().getPurchaseToken()).subscriptionId(decodedData.getSubscriptionNotification().getSubscriptionId()).build();
        if (Objects.nonNull(googlePlayCallbackRequest) && NOTIFICATIONS_TYPE_ALLOWED.contains(googlePlayCallbackRequest.getNotificationType())) {
            final SubscriptionNotification subscriptionNotification = decodedData.getSubscriptionNotification();
            final String purchaseToken = subscriptionNotification.getPurchaseToken();
            Optional<ReceiptDetails> lastProcessedReceiptDetails =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                            .findById(purchaseToken);
            boolean isEligible = lastProcessedReceiptDetails.isPresent();
            return DecodedNotificationWrapper.<GooglePlayCallbackRequest>builder().decodedNotification(googlePlayCallbackRequest).eligible(isEligible).build();
        }
        return DecodedNotificationWrapper.<GooglePlayCallbackRequest>builder().decodedNotification(googlePlayCallbackRequest).eligible(false).build();
    }

    private DeveloperNotification decodeData (String data) {
        byte[] valueDecoded = Base64.decodeBase64(data);
        return Utils.getData(new String(valueDecoded), DeveloperNotification.class);
    }

    @Override
    public PaymentEvent getPaymentEvent (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper) {
        String notificationType = wrapper.getDecodedNotification().getNotificationType();
        GooglePlayVerificationRequest request= new GooglePlayVerificationRequest();
        request.setPaymentDetails(GooglePlayPaymentDetails.builder().notificationType(Integer.valueOf(notificationType)).build());
        return MerchantServiceUtil.getGooglePlayEvent(request,wrapper.getDecodedNotification().getGooglePlayLatestReceiptResponse());
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
        if (Objects.isNull(latestResponse)) {
            log.info("Latest receipt not found for uid: {}, planId: {}", transaction.getUid(), transaction.getPlanId());
            code = GooglePlayStatusCodes.GOOGLE_31018;
            transaction.setStatus(TransactionStatus.FAILURE.name());
        } else {
            AnalyticService.update(GOOGLE_PLAY_RECEIPT, gson.toJson(latestResponse));
            Lock lock = wynkRedisLockService.getWynkRedisLock(latestReceipt.getPurchaseToken());
            try {
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
                        log.info("Receipt is already processed for the UID {} and planId {}", transaction.getUid(), transaction.getPlanId());
                        code = GooglePlayStatusCodes.GOOGLE_31021;
                        transaction.setStatus(TransactionStatus.FAILUREALREADYSUBSCRIBED.name());
                    } else if (receiptDetails == null && !PURCHASE_NOTIFICATION_TYPE.equals(notificationType)) {
                        log.info("This type of notification is never processed and the notification is not of purchase type for UID {} and plan Id {}", transaction.getUid(), transaction.getPlanId());
                        code = GooglePlayStatusCodes.GOOGLE_31006;
                        transaction.setStatus(TransactionStatus.FAILURE.name());
                    } else if ((receiptDetails == null && PURCHASE_NOTIFICATION_TYPE.equals(notificationType) && EnumSet.of(TransactionStatus.INPROGRESS).contains(transaction.getStatus()))) {
                        saveReceipt(transaction.getUid(), transaction.getMsisdn(), transaction.getPlanId(), transaction.getIdStr(), latestReceipt);
                        transaction.setStatus(TransactionStatus.SUCCESS.name());
                    } else {
                        final String purchaseToken = latestReceipt.getPurchaseToken();
                        if (Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_CANCELED.getNotificationTpe())) {
                            if (Long.parseLong(latestResponse.getExpiryTimeMillis()) >= receiptDetails.getExpiry()) {
                                receiptDetails.setNotificationType(notificationType);
                                receiptDetails.setRenew(transaction.getType().equals(PaymentEvent.RENEW));
                                log.info("User has cancelled the plan from app store for uid: {}, purchaseToken :{} , planId: {}", transaction.getUid(), purchaseToken, transaction.getPlanId());
                                code = GooglePlayStatusCodes.GOOGLE_31019;
                                transaction.setStatus(TransactionStatus.CANCELLED.name());
                            } else {
                                receiptDetails.setNotificationType(notificationType);
                                receiptDetails.setRenew(transaction.getType().equals(PaymentEvent.RENEW));
                                receiptDetails.setExpiry(Long.parseLong(latestResponse.getExpiryTimeMillis()));
                                log.info("User has Unsubscribed the plan from app store for uid: {}, purchaseToken :{} , planId: {}", transaction.getUid(), purchaseToken, transaction.getPlanId());
                                code = GooglePlayStatusCodes.GOOGLE_31019;
                                transaction.setStatus(TransactionStatus.CANCELLED.name());
                            }
                        } else if (Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_PAUSED.getNotificationTpe())
                                || Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_ON_HOLD.getNotificationTpe())) {
                            receiptDetails.setNotificationType(notificationType);
                            receiptDetails.setRenew(transaction.getType().equals(PaymentEvent.RENEW));
                            receiptDetails.setExpiry(System.currentTimeMillis());
                            code = GooglePlayStatusCodes.GOOGLE_31000;
                            transaction.setStatus(TransactionStatus.ONHOLD.name());
                        } else if (Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_RESTARTED.getNotificationTpe())) {
                            receiptDetails.setNotificationType(notificationType);
                            receiptDetails.setRenew(transaction.getType().equals(PaymentEvent.RENEW));
                            receiptDetails.setExpiry(Long.parseLong(latestResponse.getExpiryTimeMillis()));
                            code = GooglePlayStatusCodes.GOOGLE_31001;
                        } else if (Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_RECOVERED.getNotificationTpe())) {
                            receiptDetails.setNotificationType(notificationType);
                            receiptDetails.setExpiry(Long.parseLong(latestResponse.getExpiryTimeMillis()));
                            code = GooglePlayStatusCodes.GOOGLE_31002;
                        } else if (Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_REVOKED.getNotificationTpe())) {
                            receiptDetails.setNotificationType(notificationType);
                            receiptDetails.setRenew(transaction.getType().equals(PaymentEvent.RENEW));
                            code = GooglePlayStatusCodes.GOOGLE_31003;
                        } else if (Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_IN_GRACE_PERIOD.getNotificationTpe()) ||
                                Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED.getNotificationTpe())
                                || Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_EXPIRED.getNotificationTpe())) {
                            receiptDetails.setNotificationType(notificationType);
                            code = GooglePlayStatusCodes.GOOGLE_31000;
                        }
                        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                                .save(receiptDetails);
                    }
                    lock.unlock();
                }
            } catch (Exception e) {
                transaction.setStatus(TransactionStatus.FAILURE.name());
                log.error(BaseLoggingMarkers.PAYMENT_ERROR, "fetchAndUpdateFromSource :: raised exception for uid : {} purchaseToken : {} ", transaction.getUid(), latestReceipt.getPurchaseToken(), e);
            }
        }

        if (code != null) {
            try {
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(code.getErrorCode())).description(code.getErrorTitle()).build());
            } catch (Exception e) {
                log.error("Unable to publish event due to {}", e.getMessage());
            }

        }
    }

    private void saveReceipt (String uid, String msisdn, Integer planId, String transactionId, GooglePlayLatestReceiptResponse latestReceiptResponse) {
        final GooglePlayReceiptDetails googlePlayReceiptDetails =
                GooglePlayReceiptDetails.builder().id(latestReceiptResponse.getPurchaseToken()).paymentTransactionId(transactionId).msisdn(msisdn).uid(uid)
                        .expiry(Long.parseLong(latestReceiptResponse.getGooglePlayResponse().getExpiryTimeMillis())).planId(planId).notificationType(latestReceiptResponse.getNotificationType())
                        .subscriptionId(latestReceiptResponse.getSubscriptionId()).packageName(latestReceiptResponse.getPackageName()).service(latestReceiptResponse.getService())
                        .skuId(latestReceiptResponse.getSkuId()).renew(true).build();
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
            return getPlayStoreResponse(mockUrl, headers).getBody();
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE, "Exception while getting data from google Play API: {}", e.getMessage());
            throw new WynkRuntimeException(PaymentErrorType.PAY027);
        }
    }

    private String getApiKey (String service) {
        if (SERVICE_MUSIC.equals(service)) {
            return musicKey;
        } else if (SERVICE_RAJ_TV.equals(service)) {
            return rajTvKey;
        }
        if (SERVICE_AIRTEL_TV.equals(service)) {
            return airtelTvKey;
        }
        throw new WynkRuntimeException("This service is not configured for API Key");
    }

    private ResponseEntity<GooglePlayReceiptResponse> getPlayStoreResponse (String url, HttpHeaders headers) {
        ResponseEntity<GooglePlayReceiptResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), GooglePlayReceiptResponse.class);
        return responseEntity;
    }

    private HttpHeaders getHeaders (String service) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.AUTHORIZATION, AUTH_TOKEN_PREFIX.concat(googlePlayCacheService.get(service)));
        return headers;
    }

    private LatestReceiptResponse mapGoogleResponseToReceiptResponse (GooglePlayReceiptResponse googlePlayReceiptResponse, GooglePlayVerificationRequest request) {
        return GooglePlayLatestReceiptResponse.builder().freeTrial(false).autoRenewal(googlePlayReceiptResponse.isAutoRenewing()).googlePlayResponse(googlePlayReceiptResponse)
                .planId(cachingService.getPlanFromSku(request.getProductDetails().getSkuId()).getId()).purchaseToken(request.getPaymentDetails().getPurchaseToken())
                .extTxnId(request.getPaymentDetails().getPurchaseToken()).couponCode(googlePlayReceiptResponse.getPromotionCode()).notificationType(request.getPaymentDetails().getNotificationType())
                .subscriptionId(request.getPaymentDetails().getOrderId()).packageName(request.getAppDetails().getPackageName()).service(request.getAppDetails().getService())
                .skuId(request.getProductDetails().getSkuId()).build();
    }

    @Override
    public void acknowledgeSubscription (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        GooglePlaySubscriptionAcknowledgementRequest request = (GooglePlaySubscriptionAcknowledgementRequest) abstractPaymentAcknowledgementRequest;
        HttpHeaders headers = getHeaders(request.getAppDetails().getService());
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        GooglePlayAcknowledgeRequest body = GooglePlayAcknowledgeRequest.builder().developerPayload(request.getDeveloperPayload()).build();
        String url =
                baseUrl.concat(request.getAppDetails().getPackageName()).concat(purchaseUrl).concat(request.getProductDetails().getSkuId())
                        .concat(TOKEN).concat(request.getPaymentDetails().getPurchaseToken()).concat(ACKNOWLEDGE).concat(API_KEY_PARAM)
                        .concat(getApiKey(request.getAppDetails().getService()));
        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), GooglePlayReceiptResponse.class);
            log.info("Google acknowledged Successfully for the purchase with Purchase Token {}", request.getPaymentDetails().getPurchaseToken());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.GOOGLE_PLAY_ACKNOWLEDGEMENT_FAILURE, "Exception occurred while acknowledging google for the purchase with purchase token {}: ",
                    request.getPaymentDetails().getPurchaseToken());
            throw new WynkRuntimeException(PaymentErrorType.PAY029, e);
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
        ChargingStatusResponse statusResponse = fetchChargingStatusFromGooglePlaySource(TransactionContext.get(), transactionStatusRequest.getExtTxnId(), transactionStatusRequest.getPlanId());
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(statusResponse).build();
    }

    private ChargingStatusResponse fetchChargingStatusFromGooglePlaySource (Transaction transaction, String extTxnId, int planId) {
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
                ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).tid(transaction.getIdStr()).planId(planId);
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(planId));
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
                paymentManager.doVerifyIapV2(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString(),
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
        return GooglePlayLatestReceiptResponse.builder().freeTrial(false).googlePlayResponse(googlePlayReceiptResponse).planId(googlePlayLatestReceiptResponse.getPlanId())
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
        try {
            final GooglePlayReceiptDetails receiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findByPaymentTransactionId(paymentRenewalChargingRequest.getId());
           // final ItunesReceiptType receiptType = ItunesReceiptType.valueOf(receiptDetails.getType());
            final GooglePlayReceiptResponse googlePlayReceipt = googlePlayResponse(receiptDetails.getId(), receiptDetails.getSkuId(), receiptDetails.getPackageName(), receiptDetails.getService());

            GooglePlayVerificationRequest request= new GooglePlayVerificationRequest();
           // request.setPaymentDetails(GooglePlayPaymentDetails.builder().notificationType().build());


           final LatestReceiptResponse latestReceiptResponse = mapGoogleResponseToReceiptResponse(googlePlayReceipt, request);
            //final List<LatestReceiptInfo> filteredReceiptResponse = latestReceiptResponse.getLatestReceiptInfo().stream().filter(details -> receiptDetails.getId() == details.getOriginalTransactionId()).sorted(Comparator.comparingLong(receiptType::getExpireDate).reversed()).collect(Collectors.toList());
            //latestReceiptResponse.setLatestReceiptInfo(filteredReceiptResponse);

            fetchAndUpdateFromReceipt(transaction, (GooglePlayLatestReceiptResponse)latestReceiptResponse, receiptDetails);
            return WynkResponseEntity.<Void>builder().success(true).build();
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

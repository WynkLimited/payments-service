package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.data.enums.State;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkErrorType;
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
import in.wynk.payment.dto.gpbs.GooglePlayCallbackRequest;

import in.wynk.payment.dto.gpbs.GooglePlayLatestReceiptInfo;
import in.wynk.payment.dto.gpbs.GooglePlayNotificationType;
import in.wynk.payment.dto.gpbs.GooglePlayStatusCodes;
import in.wynk.payment.dto.gpbs.queue.GoogleAcknowledgeMessageManager;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayReceipt;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayReceiptResponse;
import in.wynk.payment.dto.gpbs.request.*;
import in.wynk.payment.dto.gpbs.view.GooglePlayReview;
import in.wynk.payment.dto.itune.ItunesVerificationRequest;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;
import in.wynk.payment.service.*;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE;
import static in.wynk.payment.dto.gpbs.GooglePlayConstant.PURCHASE_NOTIFICATION_TYPE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE;
import static in.wynk.payment.dto.gpbs.GooglePlayConstant.*;

/**
 * @author Nishesh Pandey
 */

@Slf4j
@Service(BeanConstant.GOOGLE_PLAY)
public class GooglePlayMerchantPaymentService extends AbstractMerchantPaymentStatusService
        implements GooglePlayService, IMerchantIapPaymentVerificationService, IPaymentNotificationService<Pair<GooglePlayLatestReceiptInfo, ReceiptDetails>>,
        IReceiptDetailService<Pair<GooglePlayLatestReceiptInfo, ReceiptDetails>, GooglePlayCallbackRequest> {

    private static final List<String> REFUND_NOTIFICATION = Collections.singletonList("CANCEL");

    @Value("${payment.googlePlay.baseUrl}")
    private String baseUrl;
    @Value("${payment.googlePlay.purchaseUrl}")
    private String purchaseUrl;
    @Value("${payment.googlePlay.key}")
    private String key;
    @Value("${payment.googlePlay.rajTv.privateKey}")
    private String privateKey;
    @Value("${payment.googlePlay.rajTv.privateKeyId}")
    private String privateKeyId;
    @Value("${payment.googlePlay.rajTv.clientEmail}")
    private String clientEmail;

    private final Gson gson;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final WynkRedisLockService wynkRedisLockService;
    private GooglePlayReview googlePlayReview;
    private GooglePlayCacheService googlePlayCacheService;
    private ISqsManagerService sqsMessagePublisher;


    public GooglePlayMerchantPaymentService (@Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, Gson gson, ObjectMapper mapper,
                                             ApplicationEventPublisher eventPublisher, WynkRedisLockService wynkRedisLockService, IErrorCodesCacheService errorCodesCacheServiceImpl,
                                             GooglePlayReview googlePlayReview, GooglePlayCacheService googlePlayCacheService, PaymentCachingService cachingService,
                                             ISqsManagerService sqsMessagePublisher) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.gson = gson;
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.wynkRedisLockService = wynkRedisLockService;
        this.googlePlayReview = googlePlayReview;
        this.googlePlayCacheService = googlePlayCacheService;
        this.sqsMessagePublisher = sqsMessagePublisher;
    }

    @Deprecated
    @Override
    public GooglePlayLatestReceiptResponse getLatestReceiptResponse (IapVerificationRequest iapVerificationRequest) {
        return null;
    }

    //for event notification
    @Override
    public void handleNotification (Transaction transaction, UserPlanMapping<Pair<GooglePlayLatestReceiptInfo, ReceiptDetails>> mapping) {

    }

    //for Notification
    @Override
    public UserPlanMapping<Pair<GooglePlayLatestReceiptInfo, ReceiptDetails>> getUserPlanMapping (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper) {
        return null;
    }

    //handle notification
    @Override
    public DecodedNotificationWrapper<GooglePlayCallbackRequest> isNotificationEligible (String requestPayload) {
        return null;
    }

    //for notification
    @Override
    public PaymentEvent getPaymentEvent (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper) {
        return null;
    }

    @Override
    public BaseResponse<?> verifyReceipt (LatestReceiptResponse latestReceiptResponse) {

        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final IapVerificationResponse.IapVerification.IapVerificationBuilder builder = IapVerificationResponse.IapVerification.builder();

        try {
            final Transaction transaction = TransactionContext.get();
            final GooglePlayLatestReceiptResponse response = (GooglePlayLatestReceiptResponse) latestReceiptResponse;

            fetchAndUpdateFromReceipt(transaction, response, null);

            final String clientPagePlaceHolder = PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT));
            if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {

                if (latestReceiptResponse.getSuccessUrl() != null) {
                    builder.url(new StringBuilder(latestReceiptResponse.getSuccessUrl()).toString());
                } else {
                    String success_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "success"), "${payment.success.page}");

                    builder.url(new StringBuilder(success_url).append(SessionContextHolder.getId())
                            .append(SLASH)
                            .append(sessionDTO.<String>get(OS))
                            .append(QUESTION_MARK)
                            .append(SERVICE)
                            .append(EQUAL)
                            .append(sessionDTO.<String>get(SERVICE))
                            .append(AND)
                            .append(BUILD_NO)
                            .append(EQUAL)
                            .append(sessionDTO.<Integer>get(BUILD_NO))
                            .toString());
                }


            } else {

                if (latestReceiptResponse.getFailureUrl() != null) {
                    builder.url(new StringBuilder(latestReceiptResponse.getFailureUrl()).toString());

                } else {
                    String failure_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.page}");
                    builder.url(new StringBuilder(failure_url).append(SessionContextHolder.getId())
                            .append(SLASH)
                            .append(sessionDTO.<String>get(OS))
                            .append(QUESTION_MARK)
                            .append(SERVICE)
                            .append(EQUAL)
                            .append(sessionDTO.<String>get(SERVICE))
                            .append(AND)
                            .append(BUILD_NO)
                            .append(EQUAL)
                            .append(sessionDTO.<Integer>get(BUILD_NO))
                            .toString());
                }
            }
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().data(builder.build()).build()).status(HttpStatus.OK).build();
        } catch (Exception e) {
            final String clientPagePlaceHolder = PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT));
            final String failure_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.page}");
            builder.url(new StringBuilder(failure_url).append(SessionContextHolder.getId())
                    .append(SLASH)
                    .append(sessionDTO.<String>get(OS))
                    .append(QUESTION_MARK)
                    .append(SERVICE)
                    .append(EQUAL)
                    .append(sessionDTO.<String>get(SERVICE))
                    .append(AND)
                    .append(BUILD_NO)
                    .append(EQUAL)
                    .append(sessionDTO.<Integer>get(BUILD_NO))
                    .toString());
            log.error(GOOGLE_PLAY_VERIFICATION_FAILURE, e.getMessage(), e);
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().message(e.getMessage()).success(false).data(builder.build()).build())
                    .status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public LatestReceiptResponse getLatestReceiptResponse (IapVerificationRequestV2 iapVerificationRequest) {
        final GooglePlayVerificationRequest request = (GooglePlayVerificationRequest) iapVerificationRequest;
        return getReceiptObjForUser(request, request.getAppDetails());
    }

    private void fetchAndUpdateFromReceipt (Transaction transaction, GooglePlayLatestReceiptResponse latestReceipt, GooglePlayReceiptDetails
            receiptDetails) {
        GooglePlayStatusCodes code = null;
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;

        GooglePlayReceiptResponse latestResponse = latestReceipt.getGooglePlayResponse();
        Integer notificationType= latestReceipt.getNotificationType();
        if (Objects.isNull(latestResponse)) {
            log.info("Latest receipt not found for uid: {}, planId: {}", transaction.getUid(), transaction.getPlanId());
            code = GooglePlayStatusCodes.GOOGLE_31018;
            transaction.setStatus(TransactionStatus.FAILURE.name());
        } else if (Objects.nonNull(latestResponse.getLinkedPurchaseToken()) && latestResponse.getCancelReason() == 0) {
            log.info("User has cancelled the plan from app store for uid: {}, purchaseToken :{} , planId: {}", transaction.getUid(), latestReceipt.getPurchaseToken(), transaction.getPlanId());
            code = GooglePlayStatusCodes.GOOGLE_31019;
            transaction.setStatus(TransactionStatus.FAILURE.name());
        } else {
            AnalyticService.update(GOOGLE_PLAY_RECEIPT, gson.toJson(latestResponse));

                Lock lock = wynkRedisLockService.getWynkRedisLock(latestReceipt.getPurchaseToken());
                try {
                    if (lock.tryLock(3, TimeUnit.SECONDS)) {
                        if (receiptDetails == null || !PURCHASE_NOTIFICATION_TYPE.equals(notificationType)) {
                            receiptDetails =
                                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                                            .findByPurchaseToken(latestReceipt.getPurchaseToken());
                        }
                        if (((receiptDetails == null && (PURCHASE_NOTIFICATION_TYPE.equals(notificationType)) ||
                                Objects.requireNonNull(receiptDetails).getState() != State.ACTIVE) & EnumSet.of(TransactionStatus.INPROGRESS).contains(transaction.getStatus()))) {
                            saveReceipt(transaction.getUid(), transaction.getMsisdn(), transaction.getPlanId(), transaction.getIdStr(), latestReceipt);
                            finalTransactionStatus = TransactionStatus.SUCCESS;
                        } else if (Long.parseLong(latestResponse.getExpiryTimeMillis()) > receiptDetails.getExpiry() &&
                                Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_CANCELED.getNotificationTpe())) {
                            receiptDetails.setNotificationType(notificationType);
                            receiptDetails.setRenew(false);
                            RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                                    .save(receiptDetails);
                            finalTransactionStatus = TransactionStatus.SUCCESS;
                        }else if (receiptDetails != null) {
                            //  Long.parseLong(response.getLatestReceiptInfo().getGooglePlayResponse().getExpiryTimeMillis()) > receiptDetails.getExpiry() &&
                            //        Objects.equals(notificationType, GooglePlayNotificationType.SUBSCRIPTION_CANCELED.getNotificationTpe())
                            //notification

                        } else if (((receiptDetails != null && receiptDetails.getState() == State.ACTIVE) &
                                EnumSet.of(TransactionStatus.FAILURE).contains(transaction.getStatus()))) { // added for recon
                            finalTransactionStatus = TransactionStatus.SUCCESS;
                        }
                    }
                    lock.unlock();
                } catch (Exception e) {
                    transaction.setStatus(TransactionStatus.FAILURE.name());
                    log.error(BaseLoggingMarkers.PAYMENT_ERROR, "fetchAndUpdateFromSource :: raised exception for uid : {} purchaseToken : {} ", transaction.getUid(), latestReceipt.getPurchaseToken(), e);
                } finally {
                    transaction.setStatus(finalTransactionStatus.name());
                    if ((transaction.getStatus() != TransactionStatus.SUCCESS) && code != null) {
                        eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(code.getErrorCode())).description(code.getErrorTitle()).build());
                    }
            }
        }
    }

    private void saveReceipt (String uid, String msisdn, Integer planId, String transactionId, GooglePlayLatestReceiptResponse latestReceiptResponse) {
        final GooglePlayReceiptDetails googlePlayReceiptDetails = GooglePlayReceiptDetails.builder()
                .paymentTransactionId(transactionId)
                .msisdn(msisdn)
                .uid(uid)
                .expiry(Long.parseLong(latestReceiptResponse.getGooglePlayResponse().getExpiryTimeMillis()))
                .planId(planId)
                .purchaseToken(latestReceiptResponse.getPurchaseToken())
                .notificationType(latestReceiptResponse.getNotificationType())
                .subscriptionId(latestReceiptResponse.getSubscriptionId())
                .packageName(latestReceiptResponse.getPackageName())
                .service(latestReceiptResponse.getService())
                .renew(true).build();
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).save(googlePlayReceiptDetails);
    }

    private LatestReceiptResponse getReceiptObjForUser (GooglePlayVerificationRequest request, GooglePlayAppDetails appDetails) {
        GooglePlayPaymentDetails paymentDetails = request.getPaymentDetails();
        try {
            GooglePlayReceiptResponse googlePlayReceiptResponse =
                    googlePlayResponse(paymentDetails.getPurchaseToken(), paymentDetails.getOrderId(), appDetails.getPackageName(), appDetails.getService());
            return GooglePlayLatestReceiptResponse.builder()
                    .freeTrial(false)
                    .autoRenewal(googlePlayReceiptResponse.isAutoRenewing())
                    .googlePlayResponse(googlePlayReceiptResponse)
                    .planId(cachingService.getPlanFromSku(request.getProductDetails().getPlanId()).getId())
                    .purchaseToken(paymentDetails.getPurchaseToken())
                    .extTxnId(paymentDetails.getPurchaseToken())
                    .couponCode(googlePlayReceiptResponse.getPromotionCode())
                    .notificationType(request.getPaymentDetails().getNotificationType())
                    .subscriptionId(request.getPaymentDetails().getOrderId())
                    .packageName(request.getAppDetails().getPackageName())
                    .service(request.getAppDetails().getService())
                    .build();
        } catch (Exception e) {
            log.error("Exception occurred while verifying receipt.");
            GooglePlayReceiptResponse googlePlayReceiptResponse1 = new GooglePlayReceiptResponse();
            googlePlayReceiptResponse1.setAutoRenewing(true);
            googlePlayReceiptResponse1.setExpiryTimeMillis(String.valueOf(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000));
            return GooglePlayLatestReceiptResponse.builder()
                    .freeTrial(false)
                    .autoRenewal(true)
                    .googlePlayResponse(googlePlayReceiptResponse1)
                    .planId(Integer.parseInt(request.getProductDetails().getPlanId()))
                    .purchaseToken(request.getPaymentDetails().getPurchaseToken())
                    .extTxnId(request.getPaymentDetails().getPurchaseToken())
                    .notificationType(request.getPaymentDetails().getNotificationType())
                    .subscriptionId(request.getPaymentDetails().getOrderId())
                    .packageName(request.getAppDetails().getPackageName())
                    .service(request.getAppDetails().getService())
                    .build();
        }
    }

    private GooglePlayReceiptResponse googlePlayResponse (String purchaseToken, String orderId, String packageName, String service) {
        try {
            String url = baseUrl.concat(packageName).concat(purchaseUrl).concat(orderId).concat(TOKEN).concat(purchaseToken).concat(API_KEY_PARAM).concat(key);
            HttpHeaders headers = getHeaders(service);
            return getPlayStoreResponse(url, service, headers);

        } catch (HttpStatusCodeException e) {
            log.error(PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE, "Exception while getting data from google Play: {}", e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY011, e);
        } catch (Exception e) {
            log.error(GOOGLE_PLAY_VERIFICATION_FAILURE, "failed to execute googlePlayResponse due to ", e);
            throw new WynkRuntimeException(WynkErrorType.UT999, e);
        }
    }

    private GooglePlayReceiptResponse getPlayStoreResponse (String url, String service, HttpHeaders headers) {
        ResponseEntity<GooglePlayReceiptResponse> responseEntity = null;
        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), GooglePlayReceiptResponse.class);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE, "Exception while getting data from google Play API: {}", e.getMessage());
        }
        if (responseEntity != null) {
            if (HttpStatus.UNAUTHORIZED.equals(responseEntity.getStatusCode())) {
                googlePlayCacheService.generateJwtTokenAndGetAccessToken(service, clientEmail, privateKeyId, privateKey);
                headers = getHeaders(service);
                responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), GooglePlayReceiptResponse.class);
            }
            if (responseEntity.getBody() != null) {
                return responseEntity.getBody();
            }
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY027);
    }

    private HttpHeaders getHeaders (String service) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.AUTHORIZATION, AUTH_TOKEN_PREFIX.concat(googlePlayCacheService.get(service)));
        return headers;
    }

    @Override
    public BaseResponse<GooglePlayBillingResponse> verifyReceiptDetails (GooglePlayVerificationRequest googlePlayVerificationRequest) {
        final GooglePlayBillingResponse.GooglePlayBillingData.GooglePlayBillingDataBuilder builder = GooglePlayBillingResponse.GooglePlayBillingData.builder();
        String tokenId = googlePlayVerificationRequest.getPaymentDetails().getPurchaseToken();
        try {
            ReceiptDetails response = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                    .findByPurchaseToken(tokenId);
            if (response == null) {
                return null;
            }
            GooglePlayReceiptDetails receiptDetails = (GooglePlayReceiptDetails) response;
            GooglePlayPaymentDetails paymentDetails = GooglePlayPaymentDetails.builder().valid(true).orderId(googlePlayVerificationRequest.getPaymentDetails().getOrderId()).purchaseToken(receiptDetails.getPurchaseToken()).build();
            builder.paymentDetails(paymentDetails);
        } catch (Exception e) {
            log.error("Exception occurred while finding data from mongo db {} ", e.getMessage());
            e.printStackTrace();
        }
        return BaseResponse.<GooglePlayBillingResponse>builder().body(GooglePlayBillingResponse.builder().data(builder.build()).build()).status(HttpStatus.OK).build();
    }

    @Override
    public void acknowledgeSubscription (IapVerificationRequestV2 request, LatestReceiptResponse latestReceiptResponse) {
        GooglePlayVerificationRequest googlePlayVerificationRequest = (GooglePlayVerificationRequest) request;
        GooglePlayLatestReceiptResponse response = (GooglePlayLatestReceiptResponse) latestReceiptResponse;
        HttpHeaders headers = getHeaders(googlePlayVerificationRequest.getAppDetails().getService());
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        GooglePlayAcknowledgeRequest body = GooglePlayAcknowledgeRequest.builder().developerPayload(response.getGooglePlayResponse().getDeveloperPayload()).build();
        String url =
                baseUrl.concat(googlePlayVerificationRequest.getAppDetails().getPackageName()).concat(purchaseUrl).concat(googlePlayVerificationRequest.getPaymentDetails().getOrderId()).concat(TOKEN)
                        .concat(googlePlayVerificationRequest.getPaymentDetails().getPurchaseToken()).concat(ACKNOWLEDGE).concat(API_KEY_PARAM).concat(key);
        try {
            ResponseEntity<GooglePlayReceiptResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), GooglePlayReceiptResponse.class);
            if (responseEntity.getStatusCode() != HttpStatus.OK) {
                publishAsync(url, body, headers);
            }
        } catch (Exception e) {
            publishAsync(url, body, headers);
        }
    }

    private void publishAsync (String url, GooglePlayAcknowledgeRequest body, HttpHeaders headers) {
        GoogleAcknowledgeMessageManager message = GoogleAcknowledgeMessageManager.builder().url(url).body(body).headers(headers).build();
        try {
            log.info("code for amazon sqs");
           // sqsMessagePublisher.publishSQSMessage(message);
        } catch (Exception e) {
           // throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status (AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        ChargingStatusResponse statusResponse = fetchChargingStatusFromGooglePlaySource(TransactionContext.get(), transactionStatusRequest.getExtTxnId(), transactionStatusRequest.getPlanId());
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(statusResponse).build();
    }

    private ChargingStatusResponse fetchChargingStatusFromGooglePlaySource (Transaction transaction, String extTxnId, int planId) {
        if (transaction.getStatus() == TransactionStatus.FAILURE) {
            final GooglePlayReceiptDetails
                    receiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                    .findByPlanIdAndId(transaction.getPlanId(), extTxnId);
            if (Objects.nonNull(receiptDetails)) {
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
        GooglePlayVerificationRequest request = new GooglePlayVerificationRequest();
        request.setPaymentDetails(
                GooglePlayPaymentDetails.builder().purchaseToken(receiptDetails.getPurchaseToken()).orderId(receiptDetails.getSubscriptionId()).notificationType(receiptDetails.getNotificationType())
                        .build());
        GoogleUserDetails userDetails = new GoogleUserDetails();
        userDetails.setUid(transaction.getUid());
        userDetails.setMsisdn(transaction.getMsisdn());
        request.setUserDetails(userDetails);
        GooglePlayAppDetails appDetails = new GooglePlayAppDetails();
        appDetails.setPackageName(receiptDetails.getPackageName());
        appDetails.setService(receiptDetails.getService());
        request.setAppDetails(appDetails);
        return (GooglePlayLatestReceiptResponse) this.getLatestReceiptResponse(request);
    }
}

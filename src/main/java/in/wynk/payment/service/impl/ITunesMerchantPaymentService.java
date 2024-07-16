package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.audit.IAuditableListener;
import in.wynk.audit.constant.AuditConstants;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.Utils;
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
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.core.dao.repository.TestingByPassNumbersDao;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;
import in.wynk.payment.dto.itune.*;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.logging.BaseLoggingMarkers.PAYMENT_ERROR;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY011;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY026;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE;
import static in.wynk.payment.dto.itune.ItunesConstant.*;

@Slf4j
@Service(BeanConstant.ITUNES_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantIapPaymentPreVerificationService, IMerchantIapSubscriptionAcknowledgementService, IMerchantIapPaymentVerificationService, IPaymentNotificationService<Pair<LatestReceiptInfo, ReceiptDetails>>, IReceiptDetailService<Pair<LatestReceiptInfo, ReceiptDetails>, ItunesCallbackRequest>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest> {

    private static final List<String> RENEWAL_NOTIFICATION = Arrays.asList("DID_RENEW", "INTERACTIVE_RENEWAL", "DID_RECOVER");
    private static final List<String> REACTIVATION_NOTIFICATION = Collections.singletonList("DID_CHANGE_RENEWAL_STATUS");
    private static final List<String> REFUND_NOTIFICATION = Collections.singletonList("CANCEL");

    @Value("${payment.merchant.itunes.api.url}")
    private String itunesApiUrl;
    @Value("${payment.merchant.itunes.api.alt.url}")
    private String itunesApiAltUrl;

    private final Gson gson;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final WynkRedisLockService wynkRedisLockService;
    private final IAuditableListener auditingListener;

    public ITunesMerchantPaymentService(@Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, Gson gson, ObjectMapper mapper, PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher, WynkRedisLockService wynkRedisLockService, IErrorCodesCacheService errorCodesCacheServiceImpl, @Qualifier(AuditConstants.MONGO_AUDIT_LISTENER) IAuditableListener auditingListener) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.gson = gson;
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.wynkRedisLockService = wynkRedisLockService;
        this.auditingListener = auditingListener;
    }

    @Override
    public BaseResponse<IapVerificationResponse> verifyReceipt(LatestReceiptResponse latestReceiptResponse) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final IapVerificationResponse.IapVerification.IapVerificationBuilder builder = IapVerificationResponse.IapVerification.builder();
        try {
            final Transaction transaction = TransactionContext.get();
            final ItunesLatestReceiptResponse response = (ItunesLatestReceiptResponse) latestReceiptResponse;

            if(!EnumSet.of(TransactionStatus.SUCCESS).contains(transaction.getStatus())) {
                fetchAndUpdateFromReceipt(transaction, response, null);
            }
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
            log.error(ITUNES_VERIFICATION_FAILURE, e.getMessage(), e);
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().message(e.getMessage()).success(false).data(builder.build()).build()).status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public LatestReceiptResponse getLatestReceiptResponse(IapVerificationRequest iapVerificationRequest) {
        final ItunesVerificationRequest request = (ItunesVerificationRequest) iapVerificationRequest;
        final ItunesReceiptType receiptType = ItunesReceiptType.getReceiptType(request.getReceipt());
        final ItunesReceipt itunesReceipt = getReceiptObjForUser(request.getReceipt(), receiptType, request.getMsisdn());
        LatestReceiptResponse response = getLatestReceiptResponseInternal(request.getReceipt(), itunesReceipt, receiptType);
        response.setSuccessUrl(request.getSuccessUrl());
        response.setFailureUrl(request.getFailureUrl());
        return response;
    }

    //Needs to be completed for v2 request
    @Override
    public LatestReceiptResponse getLatestReceiptResponse(IapVerificationRequestV2 iapVerificationRequest) {
        return null;
    }

    private ItunesLatestReceiptResponse getLatestReceiptResponseInternal(String decodedReceipt, ItunesReceipt itunesReceipt, ItunesReceiptType receiptType) {
        if (CollectionUtils.isNotEmpty(itunesReceipt.getLatestReceiptInfoList())) {
            ItunesReceiptType type = ItunesReceiptType.getReceiptType(decodedReceipt);
            final String suppliedSkuId = type.getOrDefault(decodedReceipt, PaymentConstants.SKU_ID, StringUtils.EMPTY);
            final LatestReceiptInfo latestReceiptInfo = itunesReceipt.getLatestReceiptInfoList().stream().filter(receipt -> receipt.getProductId().equalsIgnoreCase(suppliedSkuId)).findAny().orElse(itunesReceipt.getLatestReceiptInfoList().get(0));
            boolean autoRenewal = false;
            if (CollectionUtils.isNotEmpty(itunesReceipt.getPendingRenewalInfo())) {
                autoRenewal = itunesReceipt.getPendingRenewalInfo().stream().filter(pendingRenewal -> !StringUtils.isEmpty(latestReceiptInfo.getProductId()) && latestReceiptInfo.getProductId().equalsIgnoreCase(pendingRenewal.getAutoRenewProductId()) && pendingRenewal.getAutoRenewStatus().equals("1") && pendingRenewal.getOriginalTransactionId().equals(latestReceiptInfo.getOriginalTransactionId())).findAny().isPresent();
            }
            AnalyticService.update(ALL_ITUNES_RECEIPT, gson.toJson(latestReceiptInfo));
            String skuId = latestReceiptInfo.getProductId();
            if (StringUtils.isNotBlank(skuId)) {
                if (cachingService.containsSku(skuId)) {
                    skuId = cachingService.getNewSku(skuId);
                }
                PlanDTO planDTO = cachingService.getPlanFromSku(skuId);
                if (Objects.nonNull(planDTO)) {
                    return ItunesLatestReceiptResponse.builder()
                            .planId(planDTO.getId())
                            .autoRenewal(autoRenewal)
                            .itunesReceiptType(receiptType)
                            .decodedReceipt(decodedReceipt)
                            .service(planDTO.getService())
                            .couponCode(latestReceiptInfo.getOfferCodeRefName())
                            .extTxnId(latestReceiptInfo.getOriginalTransactionId())
                            .pendingRenewalInfo(itunesReceipt.getPendingRenewalInfo())
                            .latestReceiptInfo(itunesReceipt.getLatestReceiptInfoList())
                            .freeTrial(Boolean.parseBoolean(latestReceiptInfo.getIsTrialPeriod()) || Boolean.parseBoolean(latestReceiptInfo.getIsInIntroOfferPeriod()))
                            .build();
                }
            }
        }
        throw new WynkRuntimeException(PAY011);
    }

    @Override
    public void handleNotification(Transaction transaction, UserPlanMapping<Pair<LatestReceiptInfo, ReceiptDetails>> mapping) {
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        final LatestReceiptInfo receiptInfo = mapping.getMessage().getFirst();
        final ItunesReceiptDetails receiptDetails = (ItunesReceiptDetails) mapping.getMessage().getSecond();
        final ItunesReceiptType receiptType = ItunesReceiptType.valueOf(receiptDetails.getType());
        //Need to add check for itunes txn Id in merchant transaction table to achieve idempotency
        final long expireTimestamp = NumberUtils.toLong(receiptInfo.getExpiresDateMs());
        final long cancellationTimestamp = NumberUtils.toLong(receiptInfo.getCancellationDateMs());
        if (expireTimestamp > System.currentTimeMillis() || (transaction.getType() == PaymentEvent.CANCELLED && cancellationTimestamp <= System.currentTimeMillis())) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
        }
        final String oldTransactionId = receiptDetails.getPaymentTransactionId();
        transaction.setStatus(finalTransactionStatus.name());
        final ItunesReceiptDetails itunesIdUidMapping = ItunesReceiptDetails.builder()
                .type(receiptType.name())
                .id(receiptInfo.getOriginalTransactionId())
                .uid(transaction.getUid())
                .msisdn(transaction.getMsisdn())
                .planId(transaction.getPlanId())
                .service(mapping.getService())
                .paymentTransactionId(transaction.getIdStr())
                .receiptTransactionId(receiptType.getTransactionId(receiptInfo))
                .expiry(receiptType.getExpireDate(receiptInfo))
                .receipt(receiptDetails.getReceipt())
                .build();
        auditingListener.onBeforeSave(itunesIdUidMapping);
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).save(itunesIdUidMapping);
    }

    private ItunesLatestReceiptResponse getItunesLatestReceiptResponse(Transaction transaction, String decodedReceipt) {
        return (ItunesLatestReceiptResponse) this.getLatestReceiptResponse(ItunesVerificationRequest.builder()
                .receipt(decodedReceipt)
                .uid(transaction.getUid())
                .msisdn(transaction.getMsisdn())
                .build());
    }

    @SneakyThrows
    @ClientAware(clientAlias = "#transaction.clientAlias")
    private ChargingStatusResponse fetchChargingStatusFromItunesSource(Transaction transaction, String extTxnId, int planId) {
        if (transaction.getStatus() == TransactionStatus.FAILURE) {
            final ItunesReceiptDetails receiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findByPlanIdAndId(transaction.getPlanId(), extTxnId);
            if (Objects.nonNull(receiptDetails)) {
                fetchAndUpdateFromReceipt(transaction, getItunesLatestReceiptResponse(transaction, receiptDetails.getReceipt()), receiptDetails);
            } else {
                log.error(PAYMENT_RECONCILIATION_FAILURE, "unable to reconcile since receipt is not present for original itunes id {}", extTxnId);
            }
        }
        ChargingStatusResponse.ChargingStatusResponseBuilder<?, ?> responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).tid(transaction.getIdStr()).planId(planId);
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(planId,transaction.getMsisdn()));
        }
        return responseBuilder.build();
    }

    private void fetchAndUpdateFromReceipt(Transaction transaction, ItunesLatestReceiptResponse itunesLatestReceiptResponse, ItunesReceiptDetails receiptDetails) {
        try {
            ItunesStatusCodes code = null;
            final ItunesReceiptType receiptType = itunesLatestReceiptResponse.getItunesReceiptType();
            final List<LatestReceiptInfo> latestReceiptInfoList = itunesLatestReceiptResponse.getLatestReceiptInfo();
            if (CollectionUtils.isEmpty(latestReceiptInfoList)) {
                log.info("Latest receipt not found for uid: {}, planId: {}", transaction.getUid(), transaction.getPlanId());
                code = ItunesStatusCodes.APPLE_21018;
                transaction.setStatus(TransactionStatus.FAILURE.name());
            } else {
                LatestReceiptInfo latestReceiptInfo = latestReceiptInfoList.get(0);
                AnalyticService.update(ALL_ITUNES_RECEIPT, gson.toJson(latestReceiptInfo));
                final long expireTimestamp = receiptType.getExpireDate(latestReceiptInfo);
                if (expireTimestamp == 0 || expireTimestamp < System.currentTimeMillis()) {
                    log.info("Latest receipt found expired for uid: {}, expireTimestamp :{} , planId: {}", transaction.getUid(), expireTimestamp, transaction.getPlanId());
                    code = ItunesStatusCodes.APPLE_21015;
                    transaction.setStatus(TransactionStatus.FAILURE.name());
                } else {
                    final String originalITunesTrxnId = latestReceiptInfo.getOriginalTransactionId();
                    final String itunesTrxnId = latestReceiptInfo.getTransactionId();
                    Lock lock = wynkRedisLockService.getWynkRedisLock(originalITunesTrxnId);
                    if (lock.tryLock(3, TimeUnit.SECONDS)) {
                        if (Objects.isNull(receiptDetails)) {
                            receiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findByPlanIdAndId(transaction.getPlanId(), originalITunesTrxnId);
                        }
                        if (receiptType.getCancellationDate(latestReceiptInfo) <= System.currentTimeMillis()) {
                            log.info("User has cancelled the plan from app store for uid: {}, ITunesId :{} , planId: {}", transaction.getUid(), originalITunesTrxnId, transaction.getPlanId());
                            code = ItunesStatusCodes.APPLE_21019;
                            transaction.setStatus(TransactionStatus.CANCELLED.name());
                        } else if (!isReceiptEligible(latestReceiptInfoList, receiptType, receiptDetails)) {
                            log.info("ItunesIdUidMapping found for uid: {}, ITunesId :{} , planId: {}", transaction.getUid(), originalITunesTrxnId, transaction.getPlanId());
                            code = ItunesStatusCodes.APPLE_21016;
                            transaction.setStatus(TransactionStatus.FAILUREALREADYSUBSCRIBED.name());
                        } else {
                            if (!StringUtils.isBlank(originalITunesTrxnId) && !StringUtils.isBlank(itunesTrxnId)) {
                                final ItunesReceiptDetails itunesIdUidMapping = ItunesReceiptDetails.builder()
                                        .type(receiptType.name())
                                        .id(originalITunesTrxnId)
                                        .uid(transaction.getUid())
                                        .msisdn(transaction.getMsisdn())
                                        .planId(transaction.getPlanId())
                                        .paymentTransactionId(transaction.getIdStr())
                                        .service(itunesLatestReceiptResponse.getService())
                                        .receiptTransactionId(receiptType.getTransactionId(latestReceiptInfo))
                                        .expiry(receiptType.getExpireDate(latestReceiptInfo))
                                        .receipt(itunesLatestReceiptResponse.getDecodedReceipt())
                                        .build();
                                auditingListener.onBeforeSave(itunesIdUidMapping);
                                RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).save(itunesIdUidMapping);
                                transaction.setStatus(TransactionStatus.SUCCESS.name());
                            } else {
                                log.info("originalITunesTrxnId or itunesTrxnId found empty for uid: {}, ITunesId :{} , planId: {}", transaction.getUid(), originalITunesTrxnId, transaction.getPlanId());
                                code = ItunesStatusCodes.APPLE_21017;
                                transaction.setStatus(TransactionStatus.FAILURE.name());
                            }
                        }
                        lock.unlock();
                    }
                }
            }
            if (transaction.getStatus() != TransactionStatus.SUCCESS && Objects.nonNull(code)) {
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(code.getErrorCode())).description(code.getErrorTitle()).build());
            }
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            log.error(BaseLoggingMarkers.PAYMENT_ERROR, "fetchAndUpdateFromSource :: raised exception for uid : {} receipt : {} ", transaction.getUid(), itunesLatestReceiptResponse.getDecodedReceipt(), e);
        }
    }

    private boolean isReceiptEligible(List<LatestReceiptInfo> allReceipt, ItunesReceiptType newReceiptType, ItunesReceiptDetails oldReceipt) {
        if (oldReceipt != null && oldReceipt.getState() == State.ACTIVE) {
            ItunesReceiptType oldReceiptType = ItunesReceiptType.valueOf(oldReceipt.getType());
            Optional<LatestReceiptInfo> oldReceiptOption = allReceipt.stream().filter(receipt -> (((oldReceipt.isTransactionIdPresent() && oldReceiptType.getTransactionId(receipt).equalsIgnoreCase(oldReceipt.getReceiptTransactionId())) || oldReceiptType.getExpireDate(receipt) == oldReceipt.getExpiry()))).findAny();
            if (oldReceiptOption.isPresent()) {
                LatestReceiptInfo oldReceiptInfo = oldReceiptOption.get();
                if (!oldReceipt.isTransactionIdPresent()){
                    ItunesReceiptDetails receiptDetails = ItunesReceiptDetails.builder().receipt(oldReceipt.getReceipt()).service(oldReceipt.getService()).msisdn(oldReceipt.getMsisdn()).planId(oldReceipt.getPlanId()).expiry(oldReceipt.getExpiry()).type(oldReceipt.getType()).uid(oldReceipt.getUid()).id(oldReceipt.getId()).receiptTransactionId(oldReceiptType.getTransactionId(oldReceiptInfo)).build();
                    auditingListener.onBeforeSave(receiptDetails);
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).save(receiptDetails);
                }
                return allReceipt.stream().anyMatch(receipt -> !newReceiptType.getTransactionId(receipt).equalsIgnoreCase(oldReceiptType.getTransactionId(oldReceiptInfo)) && newReceiptType.getExpireDate(receipt) > oldReceiptType.getExpireDate(oldReceiptInfo));
            }
        }
        return true;
    }


    private ItunesReceipt getReceiptObjForUser(String receipt, ItunesReceiptType itunesReceiptType, String msisdn) {
        ItunesReceipt receiptObj = itunesResponse(receipt, getSecret(), itunesReceiptType, itunesApiUrl);
        int status = Integer.parseInt(receiptObj.getStatus());
        if (status == 0) {
            return receiptObj;
        }
        ItunesStatusCodes responseITunesCode = ItunesStatusCodes.getItunesStatusCodes(status);
        if (responseITunesCode != null && FAILURE_CODES.contains(responseITunesCode)) {
            if (ALTERNATE_URL_FAILURE_CODES.contains(responseITunesCode)) {
                Optional<TestingByPassNumbers> optionalTestingByPassNumbers = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), TestingByPassNumbersDao.class).findById(msisdn);
                if (optionalTestingByPassNumbers.isPresent()) {
                    receiptObj = itunesResponse(receipt, getSecret(), itunesReceiptType, itunesApiAltUrl);
                    status = Integer.parseInt(receiptObj.getStatus());
                    if (status == 0) {
                        return receiptObj;
                    }
                }
            }
        }
        final String errorMessage = Objects.nonNull(responseITunesCode) ? responseITunesCode.getErrorCode() + " : " + responseITunesCode.getErrorTitle() : "Invalid Receipt and status code";
        throw new WynkRuntimeException(PAY026, errorMessage);
    }

    private ItunesReceipt itunesResponse(String receipt, String secret, ItunesReceiptType itunesReceiptType, String url) {
        String encodedValue = itunesReceiptType.getEncodedItunesData(receipt);
        try {
            Map<String, String> requestJson = new HashMap<>();
            requestJson.put(RECEIPT_TYPE, itunesReceiptType.name());
            requestJson.put(RECEIPT_DATA, encodedValue);
            requestJson.put(PASSWORD, secret);
            ResponseEntity<String> appStoreResponse = getAppStoreResponse(requestJson, url);
            return fetchReceiptObjFromAppResponse(appStoreResponse.getBody(), itunesReceiptType);
        } catch (HttpStatusCodeException e) {
            log.error(PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE, "Exception while posting data to iTunes: {}", e.getResponseBodyAsString());
            throw new WynkRuntimeException(PAY011, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE, "failed to execute getReceiptObjForUser due to ", e);
            throw new WynkRuntimeException(WynkErrorType.UT999, e);
        }
    }

    private String getSecret() {
        String secret;
        Optional<Client> optionalClient = ClientContext.getClient();
        if (optionalClient.isPresent() && optionalClient.get().<String>getMeta(CLIENT_ITUNES_SECRET).isPresent()) {
            secret = optionalClient.get().<String>getMeta(CLIENT_ITUNES_SECRET).get();
        } else {
            throw new WynkRuntimeException(ClientErrorType.CLIENT003);
        }
        return secret;
    }

    private ResponseEntity<String> getAppStoreResponse(Map<String, String> request, String url) {
        RequestEntity<String> requestEntity = new RequestEntity<>(gson.toJson(request), HttpMethod.POST, URI.create(url));
        return restTemplate.exchange(requestEntity, String.class);
    }

    private ItunesReceipt fetchReceiptObjFromAppResponse(String appStoreResponseBody, ItunesReceiptType itunesReceiptType) {
        try {
            ItunesReceipt receiptObj = new ItunesReceipt();
            if (itunesReceiptType.equals(ItunesReceiptType.SEVEN)) {
                receiptObj = mapper.readValue(appStoreResponseBody, ItunesReceipt.class);
            } else {
                String receiptBodyPlace = LATEST_EXPIRED_RECEIPT_INFO;
                if (appStoreResponseBody.contains(LATEST_RECEIPT_INFO))
                    receiptBodyPlace = LATEST_RECEIPT_INFO;
                JSONObject receiptFullJsonObj = (JSONObject) JSONValue.parseWithException(appStoreResponseBody);
                LatestReceiptInfo latestReceiptInfo = mapper.readValue(receiptFullJsonObj.get(receiptBodyPlace).toString(), LatestReceiptInfo.class);
                receiptObj.setStatus(receiptFullJsonObj.get(STATUS).toString());
                List<LatestReceiptInfo> latestReceiptInfoList = new ArrayList<>();
                latestReceiptInfoList.add(latestReceiptInfo);
                receiptObj.setLatestReceiptInfoList(latestReceiptInfoList);
            }
            if (receiptObj == null || receiptObj.getStatus() == null) {
                ItunesStatusCodes statusCode = ItunesStatusCodes.APPLE_21012;
                log.error("Receipt Object returned for response {} is not complete!", appStoreResponseBody);
                throw new WynkRuntimeException(PAY011, statusCode.getErrorTitle());
            }
            List<LatestReceiptInfo> temp = itunesReceiptType.getSubscriptionDetailJson(receiptObj);
            if (Objects.nonNull(temp)) {
                receiptObj.setLatestReceiptInfoList(temp);
            }
            return receiptObj;
        } catch (Exception ex) {
            throw new WynkRuntimeException(PaymentErrorType.PAY400, ex, "Error occurred while parsing receipt.");
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        ChargingStatusResponse statusResponse = fetchChargingStatusFromItunesSource(TransactionContext.get(), transactionStatusRequest.getExtTxnId(), transactionStatusRequest.getPlanId());
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(statusResponse).build();
    }

    @Override
    public UserPlanMapping<Pair<LatestReceiptInfo, ReceiptDetails>> getUserPlanMapping(DecodedNotificationWrapper<ItunesCallbackRequest> wrapper) {
        ItunesCallbackRequest itunesCallbackRequest = wrapper.getDecodedNotification();
        if (itunesCallbackRequest.getUnifiedReceipt() != null && NOTIFICATIONS_TYPE_ALLOWED.contains(itunesCallbackRequest.getNotificationType())) {
            //verify the receipt from server and then add txnType to mapping
            if (itunesCallbackRequest.getUnifiedReceipt().getLatestReceiptInfoList() != null) {
                String latestReceipt = itunesCallbackRequest.getUnifiedReceipt().getLatestReceipt();
                Map<String, String> map = new HashMap<>();
                map.put(RECEIPT_DATA, latestReceipt);
                latestReceipt = JSONValue.toJSONString(map);
                final ItunesReceiptType receiptType = ItunesReceiptType.getReceiptType(latestReceipt);
                ItunesReceipt receipt = itunesResponse(latestReceipt, itunesCallbackRequest.getPassword(), receiptType, itunesApiUrl);
                if (Integer.parseInt(receipt.getStatus()) == 0) {
                    final LatestReceiptInfo latestReceiptInfo = itunesCallbackRequest.getUnifiedReceipt().getLatestReceiptInfoList().get(0);
                    final String iTunesId = latestReceiptInfo.getOriginalTransactionId();
                    Optional<ReceiptDetails> optionalReceiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findById(iTunesId);
                    try {
                        AnalyticService.update(LATEST_RECEIPT_INFO, mapper.writeValueAsString(latestReceiptInfo));
                    } catch (Exception e) {
                        log.error(PAYMENT_ERROR, "unable to stringify receipt");
                    }
                    if (optionalReceiptDetails.isPresent()) {
                        ReceiptDetails details = optionalReceiptDetails.get();
                        String skuId = latestReceiptInfo.getProductId();
                        if (StringUtils.isNotBlank(skuId)) {
                            if (cachingService.containsSku(skuId)) {
                                skuId = cachingService.getNewSku(skuId);
                            }
                            PlanDTO planDTO = cachingService.getPlanFromSku(skuId);
                            boolean isFreeTrial = Boolean.parseBoolean(latestReceiptInfo.getIsTrialPeriod()) || Boolean.parseBoolean(latestReceiptInfo.getIsInIntroOfferPeriod());
                            if (isFreeTrial) {
                                if (planDTO.getLinkedFreePlanId() != -1) {
                                    return UserPlanMapping.<Pair<LatestReceiptInfo, ReceiptDetails>>builder().planId(planDTO.getLinkedFreePlanId()).msisdn(details.getMsisdn()).uid(details.getUid()).linkedTransactionId(details.getPaymentTransactionId())
                                            .message(Pair.of(latestReceiptInfo, details)).service(planDTO.getService()).build();
                                } else {
                                    log.error("No Free Trial mapping present for planId {}", planDTO.getId());
                                    throw new WynkRuntimeException(PaymentErrorType.PAY034);
                                }
                            }
                            return UserPlanMapping.<Pair<LatestReceiptInfo, ReceiptDetails>>builder().planId(planDTO.getId()).msisdn(details.getMsisdn()).uid(details.getUid()).linkedTransactionId(details.getPaymentTransactionId())
                                    .message(Pair.of(latestReceiptInfo, details)).service(planDTO.getService()).build();
                        }
                    }
                }
            }
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid Request");
    }

    @Override
    public DecodedNotificationWrapper<ItunesCallbackRequest> isNotificationEligible(String callbackRequest) {
        final ItunesCallbackRequest itunesCallbackRequest = Utils.getData(callbackRequest, ItunesCallbackRequest.class);
        if (itunesCallbackRequest.getUnifiedReceipt() != null && itunesCallbackRequest.getUnifiedReceipt().getLatestReceipt() != null && NOTIFICATIONS_TYPE_ALLOWED.contains(itunesCallbackRequest.getNotificationType())) {
            final LatestReceiptInfo latestReceiptInfo = itunesCallbackRequest.getUnifiedReceipt().getLatestReceiptInfoList().get(0);
            final String iTunesId = latestReceiptInfo.getOriginalTransactionId();
            Optional<ReceiptDetails> lastProcessedReceiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findById(iTunesId);
            boolean isEligible = lastProcessedReceiptDetails.isPresent() && !latestReceiptInfo.getTransactionId().equalsIgnoreCase(lastProcessedReceiptDetails.get().getReceiptTransactionId());
            return DecodedNotificationWrapper.<ItunesCallbackRequest>builder().decodedNotification(itunesCallbackRequest).eligible(isEligible).build();
        }
        return DecodedNotificationWrapper.<ItunesCallbackRequest>builder().decodedNotification(itunesCallbackRequest).eligible(false).build();
    }

    @Override
    public PaymentEvent getPaymentEvent(DecodedNotificationWrapper<ItunesCallbackRequest> wrapper, String productType) {
        String notificationType = wrapper.getDecodedNotification().getNotificationType();
        PaymentEvent event;
        if (RENEWAL_NOTIFICATION.contains(notificationType)) {
            event = PaymentEvent.RENEW;
        } else if (REACTIVATION_NOTIFICATION.contains(notificationType)) {
            if (Boolean.parseBoolean(wrapper.getDecodedNotification().getAutoRenewStatus())) {
                event = PaymentEvent.SUBSCRIBE;
            } else {
                event = PaymentEvent.UNSUBSCRIBE;
            }
        } else if (REFUND_NOTIFICATION.contains(notificationType)) {
            event = PaymentEvent.CANCELLED;
        } else {
            throw new WynkRuntimeException(WynkErrorType.UT001, "Invalid notification type");
        }
        return event;
    }

    private boolean isAutoRenewalOff(String autoRenewableProductId, List<PendingRenewalInfo> pendingRenewalInfo) {
        return pendingRenewalInfo.stream().filter(pendingRenew -> pendingRenew.getAutoRenewProductId().equals(autoRenewableProductId) || pendingRenew.getProductId().equals(autoRenewableProductId)).anyMatch(pendingInfo -> pendingInfo.getAutoRenewStatus().equalsIgnoreCase("0"));
    }

    @Override
    public WynkResponseEntity<Void> doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        final Transaction transaction = TransactionContext.get();
        try {
            Optional<Transaction> oldTransaction = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).findById(paymentRenewalChargingRequest.getId());
            final ItunesReceiptDetails receiptDetails;
            if (oldTransaction.get().getStatus() != TransactionStatus.SUCCESS) {
                String lastSuccessTransactionId = getLastSuccessTransactionId(oldTransaction.get());
                receiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findByPaymentTransactionId(lastSuccessTransactionId);
            } else {
                receiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findByPaymentTransactionId(paymentRenewalChargingRequest.getId());
            }
            final ItunesReceiptType receiptType = ItunesReceiptType.valueOf(receiptDetails.getType());
            final ItunesReceipt itunesReceipt = getReceiptObjForUser(receiptDetails.getReceipt(), receiptType, receiptDetails.getMsisdn());
            final ItunesLatestReceiptResponse latestReceiptResponse = getLatestReceiptResponseInternal(receiptDetails.getReceipt(), itunesReceipt, receiptType);
            final List<LatestReceiptInfo> filteredReceiptResponse = latestReceiptResponse.getLatestReceiptInfo().stream().filter(details -> receiptDetails.getId().equals(details.getOriginalTransactionId())).sorted(Comparator.comparingLong(receiptType::getExpireDate).reversed()).collect(Collectors.toList());
            latestReceiptResponse.setLatestReceiptInfo(filteredReceiptResponse);
            fetchAndUpdateFromReceipt(transaction, latestReceiptResponse, receiptDetails);
            return WynkResponseEntity.<Void>builder().success(true).build();
        } catch (Exception e) {
            if (WynkRuntimeException.class.isAssignableFrom(e.getClass())) {
                final WynkRuntimeException exception = (WynkRuntimeException) e;
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(exception.getErrorCode())).description(exception.getErrorTitle()).build());
            }
            log.error("Unable to do renewal for the transaction {}, error message {}", transaction.getId(), e.getMessage(), e);
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException(PaymentErrorType.PAY026, e);
        }
    }

    private String getLastSuccessTransactionId (Transaction transaction) {
        if (transaction.getType() == PaymentEvent.RENEW) {
            PaymentRenewal renewal =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class).findById(transaction.getIdStr())
                            .orElse(null);
            return Objects.nonNull(renewal) ? renewal.getLastSuccessTransactionId() : null;
        }
        return null;
    }

    public boolean supportsRenewalReconciliation() {
        return false;
    }

    @Override
    public void verifyRequest (IapVerificationRequestV2Wrapper iapVerificationRequestV2Wrapper) {
        throw new WynkRuntimeException("Method is not implemented");
    }

    @Override
    public void acknowledgeSubscription (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        throw new WynkRuntimeException("Method is not implemented");
    }

    @Override
    public void publishAsync (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        throw new WynkRuntimeException("Method is not implemented");
    }
}
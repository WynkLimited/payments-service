package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.Utils;
import in.wynk.data.enums.State;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.ItunesReceiptDetails;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.entity.TestingByPassNumbers;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.TestingByPassNumbersDao;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.DecodedNotificationWrapper;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.UserPlanMapping;
import in.wynk.payment.dto.itune.*;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY011;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE;
import static in.wynk.payment.dto.itune.ItunesConstant.*;

@Slf4j
@Service(BeanConstant.ITUNES_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantIapPaymentVerificationService, IPaymentNotificationService<LatestReceiptInfo>, IReceiptDetailService<LatestReceiptInfo, ItunesCallbackRequest> {

    private static final List<String> RENEWAL_NOTIFICATION = Arrays.asList("DID_RENEW", "INTERACTIVE_RENEWAL", "DID_RECOVER");

    @Value("${payment.merchant.itunes.api.url}")
    private String itunesApiUrl;
    @Value("${payment.merchant.itunes.api.alt.url}")
    private String itunesApiAltUrl;
    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;
    @Value("${payment.failure.page}")
    private String FAILURE_PAGE;

    private final Gson gson;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final ReceiptDetailsDao receiptDetailsDao;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final TestingByPassNumbersDao testingByPassNumbersDao;

    public ITunesMerchantPaymentService(@Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, Gson gson, ObjectMapper mapper, ReceiptDetailsDao receiptDetailsDao, PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher, TestingByPassNumbersDao testingByPassNumbersDao) {
        super(cachingService);
        this.gson = gson;
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.receiptDetailsDao = receiptDetailsDao;
        this.testingByPassNumbersDao = testingByPassNumbersDao;
    }

    @Override
    public BaseResponse<IapVerificationResponse> verifyReceipt(LatestReceiptResponse latestReceiptResponse) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final IapVerificationResponse.IapVerification.IapVerificationBuilder builder = IapVerificationResponse.IapVerification.builder();
        try {
            final Transaction transaction = TransactionContext.get();
            final ItunesLatestReceiptResponse response = (ItunesLatestReceiptResponse) latestReceiptResponse;
            fetchAndUpdateFromReceipt(transaction, response);
            if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                builder.url(new StringBuilder(SUCCESS_PAGE).append(SessionContextHolder.getId())
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
            } else {
                builder.url(new StringBuilder(FAILURE_PAGE).append(SessionContextHolder.getId())
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
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().data(builder.build()).build()).status(HttpStatus.OK).build();
        } catch (Exception e) {
            builder.url(new StringBuilder(FAILURE_PAGE).append(SessionContextHolder.getId())
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
        if (CollectionUtils.isNotEmpty(itunesReceipt.getLatestReceiptInfoList())) {
            final LatestReceiptInfo latestReceiptInfo = itunesReceipt.getLatestReceiptInfoList().get(0);
            boolean autoRenewal = false;
            if(CollectionUtils.isNotEmpty(itunesReceipt.getPendingRenewalInfo())) {
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
                            .decodedReceipt(request.getReceipt())
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
    public void handleNotification(Transaction transaction, UserPlanMapping<LatestReceiptInfo> mapping) {
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        LatestReceiptInfo receiptInfo = mapping.getMessage();
        //Need to add check for itunes txn Id in merchant transaction table to achieve idempotency
        final long expireTimestamp = NumberUtils.toLong(receiptInfo.getExpiresDateMs());
        if (expireTimestamp > System.currentTimeMillis() || transaction.getType() == PaymentEvent.CANCELLED) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
        }
        transaction.setStatus(finalTransactionStatus.name());
    }

    private ItunesLatestReceiptResponse getItunesLatestReceiptResponse(Transaction transaction, String decodedReceipt) {
        return (ItunesLatestReceiptResponse) this.getLatestReceiptResponse(ItunesVerificationRequest.builder()
                .receipt(decodedReceipt)
                .uid(transaction.getUid())
                .msisdn(transaction.getMsisdn())
                .build());
    }

    @ClientAware(clientAlias = "#transaction.clientAlias")
    private ChargingStatusResponse fetchChargingStatusFromItunesSource(Transaction transaction, String extTxnId, int planId) {
        if (transaction.getStatus() == TransactionStatus.FAILURE) {
            final ItunesReceiptDetails receiptDetails = receiptDetailsDao.findByPlanIdAndId(transaction.getPlanId(), extTxnId);
            if (Objects.nonNull(receiptDetails)) {
                fetchAndUpdateFromReceipt(transaction, getItunesLatestReceiptResponse(transaction, receiptDetails.getReceipt()));
            } else {
                log.error(PAYMENT_RECONCILIATION_FAILURE, "unable to reconcile since receipt is not present for original itunes id {}", extTxnId);
            }
        }
        ChargingStatusResponse.ChargingStatusResponseBuilder<?,?> responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).tid(transaction.getIdStr()).planId(planId);
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(planId));
        }
        return responseBuilder.build();
    }

    private void fetchAndUpdateFromReceipt(Transaction transaction, ItunesLatestReceiptResponse itunesLatestReceiptResponse) {
        try {
            ItunesStatusCodes code = null;
            final ItunesReceiptType receiptType = itunesLatestReceiptResponse.getItunesReceiptType();
            final List<LatestReceiptInfo> latestReceiptInfoList = itunesLatestReceiptResponse.getLatestReceiptInfo();
            if(CollectionUtils.isEmpty(latestReceiptInfoList)) {
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
                    final ItunesReceiptDetails receiptDetails = receiptDetailsDao.findByPlanIdAndId(transaction.getPlanId(), originalITunesTrxnId);
                    if (isAutoRenewalOff(latestReceiptInfo.getProductId(), itunesLatestReceiptResponse.getPendingRenewalInfo())) {
                        log.info("User has unsubscribed the plan from app store for uid: {}, ITunesId :{} , planId: {}", transaction.getUid(), originalITunesTrxnId, transaction.getPlanId());
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
                                    .transactionId(receiptType.getTransactionId(latestReceiptInfo))
                                    .expiry(receiptType.getExpireDate(latestReceiptInfo))
                                    .receipt(itunesLatestReceiptResponse.getDecodedReceipt())
                                    .build();
                            receiptDetailsDao.save(itunesIdUidMapping);
                            transaction.setStatus(TransactionStatus.SUCCESS.name());
                        } else {
                            log.info("originalITunesTrxnId or itunesTrxnId found empty for uid: {}, ITunesId :{} , planId: {}", transaction.getUid(), originalITunesTrxnId, transaction.getPlanId());
                            code = ItunesStatusCodes.APPLE_21017;
                            transaction.setStatus(TransactionStatus.FAILURE.name());
                        }
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
            Optional<LatestReceiptInfo> oldReceiptOption = allReceipt.stream().filter(receipt -> (((oldReceipt.isTransactionIdPresent() && oldReceipt.getTransactionId() == oldReceiptType.getTransactionId(receipt)) || oldReceiptType.getExpireDate(receipt) == oldReceipt.getExpiry()))).findAny();
            if (oldReceiptOption.isPresent()) {
                LatestReceiptInfo oldReceiptInfo = oldReceiptOption.get();
                if (!oldReceipt.isTransactionIdPresent())
                    receiptDetailsDao.save(ItunesReceiptDetails.builder().receipt(oldReceipt.getReceipt()).msisdn(oldReceipt.getMsisdn()).planId(oldReceipt.getPlanId()).expiry(oldReceipt.getExpiry()).type(oldReceipt.getType()).uid(oldReceipt.getUid()).id(oldReceipt.getId()).transactionId(oldReceiptType.getTransactionId(oldReceiptInfo)).build());
                return allReceipt.stream().anyMatch(receipt -> newReceiptType.getTransactionId(receipt) != oldReceiptType.getTransactionId(oldReceiptInfo) && newReceiptType.getExpireDate(receipt) > oldReceiptType.getExpireDate(oldReceiptInfo));
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
                Optional<TestingByPassNumbers> optionalTestingByPassNumbers = testingByPassNumbersDao.findById(msisdn);
                if (optionalTestingByPassNumbers.isPresent()) {
                    receiptObj = itunesResponse(receipt, getSecret(), itunesReceiptType, itunesApiAltUrl);
                    status = Integer.parseInt(receiptObj.getStatus());
                    if (status == 0) {
                        return receiptObj;
                    }
                }
            }
        }
        throw new WynkRuntimeException(PAY011, "Invalid receipt");
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
                JSONObject receiptFullJsonObj = (JSONObject) JSONValue.parseWithException(appStoreResponseBody);
                LatestReceiptInfo latestReceiptInfo = mapper.readValue(receiptFullJsonObj.get(LATEST_RECEIPT_INFO).toString(), LatestReceiptInfo.class);
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
            receiptObj.setLatestReceiptInfoList(itunesReceiptType
                    .getSubscriptionDetailJson(receiptObj)
                    .stream()
                    .sorted(Comparator.comparingLong(itunesReceiptType::getExpireDate).reversed())
                    .collect(Collectors.toList()));
            return receiptObj;
        } catch (Exception ex) {
            throw new WynkRuntimeException(PaymentErrorType.PAY400, "Error occurred while parsing receipt.");
        }
    }

    @Override
    public BaseResponse<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        ChargingStatusResponse statusResponse = fetchChargingStatusFromItunesSource(TransactionContext.get(), transactionStatusRequest.getExtTxnId(), transactionStatusRequest.getPlanId());
        return BaseResponse.<AbstractChargingStatusResponse>builder().status(HttpStatus.OK).body(statusResponse).build();
    }

    @Override
    public UserPlanMapping<LatestReceiptInfo> getUserPlanMapping(DecodedNotificationWrapper<ItunesCallbackRequest> wrapper) {
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
                    Optional<ReceiptDetails> optionalReceiptDetails = receiptDetailsDao.findById(iTunesId);
                    if (optionalReceiptDetails.isPresent()) {
                        ReceiptDetails details = optionalReceiptDetails.get();
                        return UserPlanMapping.<LatestReceiptInfo>builder().planId(details.getPlanId()).msisdn(details.getMsisdn())
                                .uid(details.getUid()).message(latestReceiptInfo).build();
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
            return DecodedNotificationWrapper.<ItunesCallbackRequest>builder().decodedNotification(itunesCallbackRequest).eligible(receiptDetailsDao.existsById(iTunesId)).build();
        }
        return DecodedNotificationWrapper.<ItunesCallbackRequest>builder().decodedNotification(itunesCallbackRequest).eligible(false).build();
    }

    @Override
    public PaymentEvent getPaymentEvent(DecodedNotificationWrapper<ItunesCallbackRequest> wrapper) {
        String notificationType = wrapper.getDecodedNotification().getNotificationType();
        PaymentEvent event;
        if (RENEWAL_NOTIFICATION.contains(notificationType)) {
            event = PaymentEvent.RENEW;
        } else if ("DID_CHANGE_RENEWAL_STATUS".equalsIgnoreCase(notificationType)) {
            if (Boolean.parseBoolean(wrapper.getDecodedNotification().getAutoRenewStatus())) {
                event = PaymentEvent.SUBSCRIBE;
            } else {
                event = PaymentEvent.UNSUBSCRIBE;
            }
        } else {
            throw new WynkRuntimeException(WynkErrorType.UT001, "Invalid notification type");
        }
        return event;
    }

    private boolean isAutoRenewalOff(String autoRenewableProductId, List<PendingRenewalInfo> pendingRenewalInfo) {
        return pendingRenewalInfo.stream().filter(pendingRenew -> pendingRenew.getAutoRenewProductId().equals(autoRenewableProductId) || pendingRenew.getProductId().equals(autoRenewableProductId)).anyMatch(pendingInfo -> pendingInfo.getAutoRenewStatus().equalsIgnoreCase("0"));
    }

}
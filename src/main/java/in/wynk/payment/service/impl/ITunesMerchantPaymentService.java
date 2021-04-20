package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import in.wynk.data.enums.State;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.ItunesReceiptDetails;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.entity.TestingByPassNumbers;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.TestingByPassNumbersDao;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.itune.*;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.IapVerificationResponse;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY011;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE;
import static in.wynk.payment.dto.itune.ItunesConstant.*;

@Slf4j
@Service(BeanConstant.ITUNES_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantIapPaymentVerificationService, IMerchantPaymentStatusService, IMerchantPaymentCallbackService, IReceiptDetailService {

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
        final List<LatestReceiptInfo> userLatestReceipts = getReceiptObjForUser(request.getReceipt(), receiptType, request.getMsisdn(), request.getUid(), request.getPlanId());
        if (CollectionUtils.isNotEmpty(userLatestReceipts)) {
            final LatestReceiptInfo latestReceiptInfo = userLatestReceipts.get(0);
            AnalyticService.update(ALL_ITUNES_RECEIPT, gson.toJson(latestReceiptInfo));
            return ItunesLatestReceiptResponse.builder()
                    .itunesReceiptType(receiptType)
                    .latestReceiptInfo(userLatestReceipts)
                    .decodedReceipt(request.getReceipt())
                    .extTxnId(latestReceiptInfo.getOriginalTransactionId())
                    .freeTrial(Boolean.parseBoolean(latestReceiptInfo.getIsTrialPeriod()) || Boolean.parseBoolean(latestReceiptInfo.getIsInIntroOfferPeriod()))
                    .build();
        }
        throw new WynkRuntimeException(PAY011);
    }

    @Override
    public BaseResponse<ChargingStatusResponse> handleCallback(CallbackRequest callbackRequest) {
        Transaction transaction = TransactionContext.get();
        try {
            final ItunesCallbackRequest itunesCallbackRequest = mapper.readValue((String) callbackRequest.getBody(), ItunesCallbackRequest.class);
            if (itunesCallbackRequest.getUnifiedReceipt() != null) {
                String latestReceipt = itunesCallbackRequest.getUnifiedReceipt().getLatestReceipt();
                if (StringUtils.isNotBlank(latestReceipt)) {
                    Map<String, String> map = new HashMap<>();
                    map.put(RECEIPT_DATA, latestReceipt);
                    fetchAndUpdateFromReceipt(transaction, getItunesLatestReceiptResponse(transaction, JSONValue.toJSONString(map)));
                }
            }
            return BaseResponse.<ChargingStatusResponse>builder().body(ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build()).status(HttpStatus.OK).build();
        } catch (Exception e) {
            throw new WynkRuntimeException(WynkErrorType.UT999, "Error while handling iTunes callback");
        }
    }

    private ItunesLatestReceiptResponse getItunesLatestReceiptResponse(Transaction transaction, String decodedReceipt) {
        return (ItunesLatestReceiptResponse) this.getLatestReceiptResponse(ItunesVerificationRequest.builder()
                .receipt(decodedReceipt)
                .uid(transaction.getUid())
                .msisdn(transaction.getMsisdn())
                .planId(transaction.getPlanId())
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
        ChargingStatusResponse.ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).tid(transaction.getIdStr()).planId(planId);
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
            LatestReceiptInfo latestReceiptInfo = latestReceiptInfoList.get(0);
            AnalyticService.update(ALL_ITUNES_RECEIPT, gson.toJson(latestReceiptInfo));
            final long expireTimestamp = receiptType.getExpireDate(latestReceiptInfo);
            if (expireTimestamp == 0 || expireTimestamp < System.currentTimeMillis()) {
                code = ItunesStatusCodes.APPLE_21015;
                transaction.setStatus(TransactionStatus.FAILURE.name());
            } else {
                final String originalITunesTrxnId = latestReceiptInfo.getOriginalTransactionId();
                final String itunesTrxnId = latestReceiptInfo.getTransactionId();
                final ItunesReceiptDetails receiptDetails = receiptDetailsDao.findByPlanIdAndId(transaction.getPlanId(), originalITunesTrxnId);
                if (!isReceiptEligible(latestReceiptInfoList, receiptType, receiptDetails)) {
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
                        code = ItunesStatusCodes.APPLE_21017;
                        transaction.setStatus(TransactionStatus.FAILURE.name());
                    }
                }
            }
            if (transaction.getStatus() != TransactionStatus.SUCCESS) {
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

    private List<LatestReceiptInfo> getReceiptObjForUser(String receipt, ItunesReceiptType itunesReceiptType, String msisdn, String uid, int planId) {
        String encodedValue = itunesReceiptType.getEncodedItunesData(receipt);
        String secret;
        ItunesStatusCodes statusCode;
        Optional<Client> optionalClient = ClientContext.getClient();
        if (optionalClient.isPresent() && optionalClient.get().<String>getMeta(CLIENT_ITUNES_SECRET).isPresent()) {
            secret = optionalClient.get().<String>getMeta(CLIENT_ITUNES_SECRET).get();
        } else {
            throw new WynkRuntimeException(ClientErrorType.CLIENT003);
        }
        if (StringUtils.isBlank(encodedValue)) {
            statusCode = ItunesStatusCodes.APPLE_21011;
            log.error(BaseLoggingMarkers.PAYMENT_ERROR, "Encoded iTunes receipt data is empty! for iTunesData {}", encodedValue);
            throw new WynkRuntimeException(PAY011, statusCode.getErrorTitle());
        }
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put(RECEIPT_TYPE, itunesReceiptType.name());
            requestJson.put(RECEIPT_DATA, encodedValue);
            requestJson.put(PASSWORD, secret);
            ResponseEntity<String> appStoreResponse = getAppStoreResponse(requestJson, itunesApiUrl);
            ItunesReceipt receiptObj = fetchReceiptObjFromAppResponse(appStoreResponse.getBody(), itunesReceiptType);
            int status = Integer.parseInt(receiptObj.getStatus());
            ItunesStatusCodes responseITunesCode = ItunesStatusCodes.getItunesStatusCodes(status);
            if (status == 0) {
                List<LatestReceiptInfo> receiptInfoList = getLatestITunesReceiptForProduct(planId, itunesReceiptType, itunesReceiptType.getSubscriptionDetailJson(receiptObj));
                return receiptInfoList;
            } else {
                if (responseITunesCode != null && FAILURE_CODES.contains(responseITunesCode)) {
                    if (ALTERNATE_URL_FAILURE_CODES.contains(responseITunesCode)) {
                        Optional<TestingByPassNumbers> optionalTestingByPassNumbers = testingByPassNumbersDao.findById(msisdn);
                        if (optionalTestingByPassNumbers.isPresent()) {
                            appStoreResponse = getAppStoreResponse(requestJson, itunesApiAltUrl);
                            receiptObj = fetchReceiptObjFromAppResponse(appStoreResponse.getBody(), itunesReceiptType);
                            status = Integer.parseInt(receiptObj.getStatus());
                            responseITunesCode = ItunesStatusCodes.getItunesStatusCodes(status);
                            if (status == 0) {
                                List<LatestReceiptInfo> receiptInfoList = getLatestITunesReceiptForProduct(planId, itunesReceiptType, itunesReceiptType.getSubscriptionDetailJson(receiptObj));
                                return receiptInfoList;
                            }
                        }
                    }
                    statusCode = responseITunesCode;
                } else {
                    statusCode = ItunesStatusCodes.APPLE_21009;
                }
                AnalyticService.update(statusCode.toString(), statusCode.getErrorTitle());
                log.error(BaseLoggingMarkers.PAYMENT_ERROR, "Failed to subscribe to iTunes: response {} request!! status : {} error {}", appStoreResponse, status, statusCode.getErrorTitle());
                throw new WynkRuntimeException(PAY011, statusCode.getErrorTitle());
            }
        } catch (HttpStatusCodeException e) {
            log.error(PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE, "Exception while posting data to iTunes for uid {} ", uid);
            throw new WynkRuntimeException(PAY011, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE, "failed to execute getReceiptObjForUser due to ", e);
            throw new WynkRuntimeException(WynkErrorType.UT999, e);
        }
    }

    private ResponseEntity<String> getAppStoreResponse(JSONObject requestJson, String url) {
        ResponseEntity<String> appStoreResponse;
        RequestEntity<String> requestEntity = new RequestEntity<>(requestJson.toJSONString(), HttpMethod.POST, URI.create(url));
        appStoreResponse = restTemplate.exchange(requestEntity, String.class);
        return appStoreResponse;
    }

    private ItunesReceipt fetchReceiptObjFromAppResponse(String appStoreResponseBody, ItunesReceiptType itunesReceiptType) throws JsonProcessingException, ParseException {
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
        return receiptObj;
    }

    @Deprecated
    private String getModifiedReceipt(String receipt) {
        String decodedReceipt;
        decodedReceipt = new String(Base64.decodeBase64(receipt), StandardCharsets.UTF_8);
        decodedReceipt = decodedReceipt.replaceAll(";(?=[^;]*$)", "");
        decodedReceipt = decodedReceipt.replaceAll(";", ",");
        decodedReceipt = decodedReceipt.replaceAll("\" = \"", "\" : \"");
        return decodedReceipt;
    }

    private List<LatestReceiptInfo> getLatestITunesReceiptForProduct(int productId, ItunesReceiptType type, List<LatestReceiptInfo> receipts) {
        PlanDTO selectedPlan = cachingService.getPlan(productId);
        String skuId = selectedPlan.getSku().get(BaseConstants.ITUNES);
        return receipts.stream()
                .filter(receipt -> filterBySku(receipt, skuId))
                .sorted(Comparator.comparingLong(type::getExpireDate).reversed())
                .collect(Collectors.toList());
    }

    private boolean filterBySku(LatestReceiptInfo receipt, String skuId) {
        return StringUtils.isNotEmpty(receipt.getProductId())
                && (cachingService.containsSku(receipt.getProductId()) ?
                StringUtils.equalsIgnoreCase(cachingService.getNewSku(receipt.getProductId()), skuId) :
                StringUtils.equalsIgnoreCase(receipt.getProductId(), skuId));
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        ChargingStatusResponse statusResponse = fetchChargingStatusFromItunesSource(TransactionContext.get(), transactionStatusRequest.getExtTxnId(), transactionStatusRequest.getPlanId());
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(statusResponse).build();
    }

    private ChargingStatusResponse fetchChargingStatusFromDataSource(Transaction transaction, int planId) {
        ChargingStatusResponse.ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder()
                .tid(transaction.getIdStr())
                .planId(planId)
                .transactionStatus(transaction.getStatus());
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            responseBuilder.validity(cachingService.validTillDate(planId));
        }
        return responseBuilder.build();
    }

    @Override
    public Optional<ReceiptDetails> getReceiptDetails(CallbackRequest callbackRequest) {
        try {
            final ItunesCallbackRequest itunesCallbackRequest = mapper.readValue((String) callbackRequest.getBody(), ItunesCallbackRequest.class);
            if (itunesCallbackRequest.getUnifiedReceipt() != null && itunesCallbackRequest.getUnifiedReceipt().getLatestReceiptInfoList() != null && NOTIFICATIONS_TYPE_ALLOWED.contains(itunesCallbackRequest.getNotificationType())) {
                LatestReceiptInfo receiptInfo = itunesCallbackRequest.getUnifiedReceipt().getLatestReceiptInfoList().stream().sorted(Comparator.comparingLong(ItunesReceiptType.SEVEN::getExpireDate).reversed()).collect(Collectors.toList()).get(0);
                String skuCode = StringUtils.isNotEmpty(receiptInfo.getProductId()) && cachingService.containsSku(receiptInfo.getProductId()) ? cachingService.getNewSku(receiptInfo.getProductId()) : receiptInfo.getProductId();
                PlanDTO plan = cachingService.getPlanFromSku(skuCode);
                final LatestReceiptInfo latestReceiptInfo = itunesCallbackRequest.getUnifiedReceipt().getLatestReceiptInfoList().get(0);
                final String iTunesId = latestReceiptInfo.getOriginalTransactionId();
                return receiptDetailsDao.findById(iTunesId).map(receiptDetails -> ItunesReceiptDetails.builder().planId(plan.getId()).receipt(((ItunesReceiptDetails)receiptDetails).getReceipt()).type(((ItunesReceiptDetails) receiptDetails).getType()).transactionId(((ItunesReceiptDetails) receiptDetails).getTransactionId()).id(receiptDetails.getId()).state(receiptDetails.getState()).msisdn(receiptDetails.getMsisdn()).uid(receiptDetails.getUid()).expiry(receiptDetails.getExpiry()).build());
            }
        } catch (Exception e) {
        }
        return Optional.empty();
    }

}
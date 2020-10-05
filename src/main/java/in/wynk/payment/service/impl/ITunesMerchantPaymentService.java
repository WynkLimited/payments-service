package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.PlanType;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.ItunesIdUidMapping;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.receipts.ItunesIdUidDao;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.itune.*;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static in.wynk.commons.constants.BaseConstants.ITUNES;
import static in.wynk.commons.constants.BaseConstants.SLASH;
import static in.wynk.commons.enums.TransactionStatus.SUCCESS;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.ITUNES_ERROR;
import static in.wynk.payment.dto.itune.ItunesConstant.*;

@Slf4j
@Service(BeanConstant.ITUNES_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService implements IMerchantIapPaymentVerificationService, IMerchantPaymentCallbackService {

    @Value("${payment.merchant.itunes.secret}")
    private String itunesSecret;
    @Value("${payment.merchant.itunes.api.url}")
    private String itunesApiUrl;
    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;

    @Autowired
    @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE)
    private RestTemplate restTemplate;

    private final Gson gson;
    private final ObjectMapper mapper;
    private final ItunesIdUidDao itunesIdUidDao;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManager;

    public ITunesMerchantPaymentService(Gson gson, ObjectMapper mapper, ItunesIdUidDao itunesIdUidDao, PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManager) {
        this.gson = gson;
        this.mapper = mapper;
        this.itunesIdUidDao = itunesIdUidDao;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public BaseResponse<Void> verifyReceipt(IapVerificationRequest iapVerificationRequest) {
        try {
            ItunesVerificationRequest request = (ItunesVerificationRequest) iapVerificationRequest;
            AnalyticService.update(request);
            final PlanDTO selectedPlan = cachingService.getPlan(request.getPlanId());
            final TransactionEvent eventType = selectedPlan.getPlanType() == PlanType.ONE_TIME_SUBSCRIPTION ? TransactionEvent.PURCHASE : TransactionEvent.SUBSCRIBE;
            final Transaction transaction = transactionManager.initiateTransaction(request.getUid(), request.getMsisdn(), selectedPlan.getId(), selectedPlan.getPrice().getAmount(), PaymentCode.ITUNES, eventType);
            transaction.putValueInPaymentMetaData(DECODED_RECEIPT, request.getReceipt());
            transactionManager.updateAndPublishSync(transaction, this::fetchAndUpdateFromReceipt);

            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.error(PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE, "Transaction is still pending at itunes end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new PaymentRuntimeException(PaymentErrorType.PAY303);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.error(PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE, "Unknown Transaction status at itunes end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new PaymentRuntimeException(PaymentErrorType.PAY304);
            } else if (transaction.getStatus().equals(SUCCESS)) {
                return BaseResponse.redirectResponse(SUCCESS_PAGE + SessionContextHolder.getId() + SLASH + ITUNES);
            } else {
                throw new PaymentRuntimeException(PaymentErrorType.PAY305);
            }
        } catch (PaymentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error(ITUNES_ERROR, e.getMessage(), e);
            throw new PaymentRuntimeException(PaymentErrorType.PAY305, e, e.getMessage());
        }
    }

    @Override
    public BaseResponse<ChargingStatusResponse> handleCallback(CallbackRequest callbackRequest) {
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        try {
            final ItunesCallbackRequest itunesCallbackRequest = mapper.readValue((String)callbackRequest.getBody(), ItunesCallbackRequest.class);
            if (itunesCallbackRequest.getLatestReceiptInfo() != null) {
                final LatestReceiptInfo latestReceiptInfo = itunesCallbackRequest.getLatestReceiptInfo();
                final String iTunesId = latestReceiptInfo.getOriginalTransactionId();
                final ItunesIdUidMapping itunesIdUidMapping = itunesIdUidDao.findByItunesId(iTunesId);
                final String uid = itunesIdUidMapping.getUid();
                final String msisdn = itunesIdUidMapping.getMsisdn();
                try {
                    final String decodedReceipt = getModifiedReceipt(itunesCallbackRequest.getLatestReceipt());
                    final PlanDTO selectedPlan = cachingService.getPlan(itunesIdUidMapping.getPlanId());
                    final TransactionEvent eventType = selectedPlan.getPlanType() == PlanType.ONE_TIME_SUBSCRIPTION ? TransactionEvent.PURCHASE : TransactionEvent.SUBSCRIBE;
                    final Transaction transaction = transactionManager.initiateTransaction(uid, msisdn, selectedPlan.getId(), selectedPlan.getPrice().getAmount(), PaymentCode.ITUNES, eventType);
                    transaction.putValueInPaymentMetaData(DECODED_RECEIPT, decodedReceipt);
                    transactionManager.updateAndPublishAsync(transaction, this::fetchAndUpdateFromReceipt);
                    finalTransactionStatus = transaction.getStatus();
                } catch (UnsupportedEncodingException e) {
                    log.error(BaseLoggingMarkers.PAYMENT_ERROR, e.getMessage(), e);
                }
            }
            return BaseResponse.<ChargingStatusResponse>builder().body(ChargingStatusResponse.builder().transactionStatus(finalTransactionStatus).build()).status(HttpStatus.OK).build();
        } catch (Exception e) {
            throw new WynkRuntimeException(WynkErrorType.UT999, "Error while handling iTunes callback");
        }
    }

    private void fetchAndUpdateFromReceipt(Transaction transaction) {
        String errorMessage = StringUtils.EMPTY;
        final String decodedReceipt = transaction.getValueFromPaymentMetaData(DECODED_RECEIPT);
        final ItunesReceiptType receiptType = ItunesReceiptType.getReceiptType(decodedReceipt);
        try {
            final List<LatestReceiptInfo> userLatestReceipts = getReceiptObjForUser(decodedReceipt, receiptType, transaction);
            if (CollectionUtils.isNotEmpty(userLatestReceipts)) {
                final LatestReceiptInfo latestReceiptInfo = userLatestReceipts.get(0);
                log.info("latest receipt object: {}", latestReceiptInfo.toString());
                final long expireTimestamp = receiptType.getExpireDate(latestReceiptInfo);
                if (expireTimestamp == 0 || expireTimestamp < System.currentTimeMillis()) {
                    errorMessage = "Empty receipt info from itunes or expired receipt";
                    log.error(BaseLoggingMarkers.PAYMENT_ERROR, "fetchAndUpdateFromSource :: empty or old receipt for uid : {} obj :{} ", transaction.getUid(), latestReceiptInfo);
                    transaction.setStatus(TransactionStatus.FAILURE.name());
                } else {
                    final String originalITunesTrxnId = latestReceiptInfo.getOriginalTransactionId();
                    final String itunesTrxnId = latestReceiptInfo.getTransactionId();
                    final ItunesIdUidMapping mapping = itunesIdUidDao.findByPlanIdAndItunesId(transaction.getPlanId(), originalITunesTrxnId);

                    if (mapping != null && !mapping.getUid().equals(transaction.getUid())) {
                        log.error(ITUNES_ERROR, "Already have subscription for the corresponding iTunes id on another account");
                        errorMessage = "Already have subscription for the corresponding iTunes id on another account";
                        transaction.setStatus(TransactionStatus.FAILUREALREADYSUBSCRIBED.name());
                    }
                    log.info("ItunesIdUidMapping found for ITunesId :{} , planId: {} = {}", originalITunesTrxnId, transaction.getPlanId(), mapping);
                    if (!StringUtils.isBlank(originalITunesTrxnId) && !StringUtils.isBlank(itunesTrxnId)) {
                        if (mapping == null) {
                            final ItunesIdUidMapping itunesIdUidMapping = ItunesIdUidMapping.builder()
                                    .uid(transaction.getUid())
                                    .msisdn(transaction.getMsisdn())
                                    .planId(transaction.getPlanId())
                                    .type(receiptType.name())
                                    .receipt(decodedReceipt)
                                    .itunesId(originalITunesTrxnId)
                                    .build();
                            itunesIdUidDao.save(itunesIdUidMapping);
                        }
                        transaction.setStatus(TransactionStatus.SUCCESS.name());
                    } else {
                        errorMessage = "Itunes transaction Id not found";
                        transaction.setStatus(TransactionStatus.FAILURE.name());
                    }
                }
            } else {
                errorMessage = "No itunes receipt found for plan id" + transaction.getPlanId();
                transaction.setStatus(TransactionStatus.FAILURE.name());
            }

            if (transaction.getStatus() != TransactionStatus.SUCCESS) {
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).description(errorMessage).build());
            }

        } catch (WynkRuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            log.error(BaseLoggingMarkers.PAYMENT_ERROR, "fetchAndUpdateFromSource :: raised exception for uid : {} receipt : {} ", transaction.getUid(), decodedReceipt, e);
            throw new WynkRuntimeException(WynkErrorType.UT999, e, "Could not process iTunes validate transaction request for uid: " + transaction.getUid());
        }
    }

    private List<LatestReceiptInfo> getReceiptObjForUser(String receipt, ItunesReceiptType itunesReceiptType, Transaction transaction) {
        Builder merchantTransactionBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            ItunesStatusCodes statusCode;
            String encodedValue = itunesReceiptType.getEncodedItunesData(receipt);
            if (StringUtils.isBlank(encodedValue)) {
                statusCode = ItunesStatusCodes.APPLE_21011;
                log.error(BaseLoggingMarkers.PAYMENT_ERROR, "Encoded iTunes receipt data is empty! for iTunesData {}", receipt);
                throw new WynkRuntimeException(PaymentErrorType.PAY011, statusCode.getErrorTitle());
            }

            ResponseEntity<String> appStoreResponse;
            JSONObject requestJson = new JSONObject();
            requestJson.put(RECEIPT_DATA, encodedValue);
            requestJson.put(PASSWORD, itunesSecret);
            merchantTransactionBuilder.request(gson.toJson(requestJson));
            RequestEntity<String> requestEntity = new RequestEntity<>(requestJson.toJSONString(), HttpMethod.POST, URI.create(itunesApiUrl));
            appStoreResponse = restTemplate.exchange(requestEntity, String.class);
            merchantTransactionBuilder.response(gson.toJson(appStoreResponse));

            String appStoreResponseBody = appStoreResponse.getBody();
            ItunesReceipt receiptObj = new ItunesReceipt();
            if (itunesReceiptType.equals(ItunesReceiptType.SEVEN)) {
                receiptObj = mapper.readValue(appStoreResponseBody, ItunesReceipt.class);
            } else {
                // Handling for type six receipts
                JSONObject receiptFullJsonObj = (JSONObject) JSONValue.parseWithException(appStoreResponseBody);
                LatestReceiptInfo latestReceiptInfo = mapper.readValue(receiptFullJsonObj.get(LATEST_RECEIPT_INFO).toString(), LatestReceiptInfo.class);
                receiptObj.setStatus(receiptFullJsonObj.get(STATUS).toString());
                List<LatestReceiptInfo> latestReceiptInfoList = new ArrayList<>();
                latestReceiptInfoList.add(latestReceiptInfo);
                receiptObj.setLatestReceiptInfoList(latestReceiptInfoList);
            }
            if (receiptObj == null || receiptObj.getStatus() == null) {
                statusCode = ItunesStatusCodes.APPLE_21012;
                log.error("Receipt Object returned for response {} is not complete!", appStoreResponseBody);
                throw new WynkRuntimeException(PaymentErrorType.PAY011, statusCode.getErrorTitle());
            }

            if (CollectionUtils.isNotEmpty(receiptObj.getLatestReceiptInfoList())) {
                // check if sorting is needed in case multiple receipt ?
                merchantTransactionBuilder.externalTransactionId(receiptObj.getLatestReceiptInfoList().get(0).getOriginalTransactionId());
            }

            eventPublisher.publishEvent(merchantTransactionBuilder.build());

            int status = Integer.parseInt(receiptObj.getStatus());
            ItunesStatusCodes responseITunesCode = ItunesStatusCodes.getItunesStatusCodes(status);
            if (status == 0) {
                return getLatestITunesReceiptForProduct(transaction.getPlanId(), itunesReceiptType, itunesReceiptType.getSubscriptionDetailJson(receiptObj));
            } else {
                if (responseITunesCode != null && FAILURE_CODES.contains(responseITunesCode)) {
                    statusCode = responseITunesCode;
                } else {
                    statusCode = ItunesStatusCodes.APPLE_21009;
                }
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(statusCode.toString()).description(statusCode.getErrorTitle()).build());
                log.error(BaseLoggingMarkers.PAYMENT_ERROR, "Failed to subscribe to iTunes: response {} request!! status : {} error {}", appStoreResponse, status, statusCode.getErrorTitle());
                throw new WynkRuntimeException(PaymentErrorType.PAY011, statusCode.getErrorTitle());
            }

        } catch (HttpStatusCodeException e) {
            merchantTransactionBuilder.response(e.getResponseBodyAsString());
            log.error(PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE,"Exception while posting data to iTunes for uid {} ", transaction.getUid());
            throw new WynkRuntimeException(PaymentErrorType.PAY011, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE, "failed to execute getReceiptObjForUser due to ", e);
            transaction.setStatus(TransactionStatus.FAILURE.name());
            throw new WynkRuntimeException(WynkErrorType.UT999, e);
        } finally {
            eventPublisher.publishEvent(merchantTransactionBuilder.build());
        }
    }

    private String getModifiedReceipt(String receipt) throws UnsupportedEncodingException {
        String decodedReceipt;
        decodedReceipt = new String(Base64.decodeBase64(receipt), StandardCharsets.UTF_8);
        decodedReceipt = decodedReceipt.replaceAll(";(?=[^;]*$)", "");
        decodedReceipt = decodedReceipt.replaceAll(";", ",");
        decodedReceipt = decodedReceipt.replaceAll("\" = \"", "\" : \"");
        return decodedReceipt;

    }

    private List<LatestReceiptInfo> getLatestITunesReceiptForProduct(int productId, ItunesReceiptType type, List<LatestReceiptInfo> receipts) {
        PlanDTO selectedPlan = cachingService.getPlan(productId);
        String skuId = selectedPlan.getSku().get(ITUNES);
        return receipts.stream()
                .filter(receipt -> StringUtils.isNotEmpty(receipt.getProductId()) && receipt.getProductId().equalsIgnoreCase(skuId))
                .sorted(Comparator.comparingLong(type::getExpireDate).reversed())
                .collect(Collectors.toList());
    }

}
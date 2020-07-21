package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.PlanType;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.ItunesIdUidMapping;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.receipts.ItunesIdUidDao;
import in.wynk.payment.core.dto.ItunesCallbackRequest;
import in.wynk.payment.core.dto.itunes.ItunesReceipt;
import in.wynk.payment.core.dto.itunes.ItunesReceiptType;
import in.wynk.payment.core.dto.itunes.LatestReceiptInfo;
import in.wynk.payment.core.dto.request.CallbackRequest;
import in.wynk.payment.core.dto.request.IapVerificationRequest;
import in.wynk.payment.core.dto.request.ItunesVerificationRequest;
import in.wynk.payment.core.dto.response.BaseResponse;
import in.wynk.payment.core.dto.response.ChargingStatus;
import in.wynk.payment.core.enums.PaymentCode;
import in.wynk.payment.core.enums.PaymentErrorType;
import in.wynk.payment.core.enums.itune.ItunesStatusCodes;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static in.wynk.payment.core.constant.itune.ItunesConstant.*;

@Slf4j
@Service(BeanConstant.ITUNES_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService implements IMerchantIapPaymentVerificationService, IMerchantPaymentCallbackService {

    @Value("${payment.merchant.itunes.secret}")
    private String itunesSecret;
    @Value("${payment.merchant.itunes.api.url}")
    private String itunesApiUrl;
    @Value("${payment.status.web.url}")
    private String statusWebUrl;

    private final Gson gson;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final ItunesIdUidDao itunesIdUidDao;
    private final PaymentCachingService cachingService;
    private final ITransactionManagerService transactionManager;
    private static final List<ItunesStatusCodes> failureCodes = Arrays.asList(ItunesStatusCodes.APPLE_21000, ItunesStatusCodes.APPLE_21002, ItunesStatusCodes.APPLE_21003, ItunesStatusCodes.APPLE_21004, ItunesStatusCodes.APPLE_21005, ItunesStatusCodes.APPLE_21007, ItunesStatusCodes.APPLE_21008, ItunesStatusCodes.APPLE_21009, ItunesStatusCodes.APPLE_21010);

    public ITunesMerchantPaymentService(Gson gson, ObjectMapper mapper, RestTemplate restTemplate, ItunesIdUidDao itunesIdUidDao, PaymentCachingService cachingService, ITransactionManagerService transactionManager) {
        this.gson = gson;
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.itunesIdUidDao = itunesIdUidDao;
        this.cachingService = cachingService;
        this.transactionManager = transactionManager;
    }

    @Override
    public BaseResponse<Void> verifyReceipt(IapVerificationRequest iapVerificationRequest) {
        try {
            ItunesVerificationRequest request = (ItunesVerificationRequest) iapVerificationRequest;
            final PlanDTO selectedPlan = cachingService.getPlan(request.getPlanId());
            final TransactionEvent eventType = selectedPlan.getPlanType() == PlanType.ONE_TIME_SUBSCRIPTION ? TransactionEvent.PURCHASE : TransactionEvent.SUBSCRIBE;
            final Transaction transaction = transactionManager.initiateTransaction(request.getUid(), request.getMsisdn(), selectedPlan.getId(), selectedPlan.getPrice().getAmount(), PaymentCode.ITUNES, eventType);
            transaction.putValueInPaymentMetaData(DECODED_RECEIPT, request.getReceipt());
            transactionManager.updateAndPublishSync(transaction, this::fetchAndUpdateFromReceipt);
            URIBuilder returnUrl = new URIBuilder(statusWebUrl);
            returnUrl.addParameter(STATUS, transaction.getStatus().name());
            return BaseResponse.redirectResponse(returnUrl.build().toString());
        } catch (Exception e) {
            throw new WynkRuntimeException(WynkErrorType.UT999, e.getMessage());
        }
    }

    @Override
    public BaseResponse<ChargingStatus> handleCallback(CallbackRequest callbackRequest) {
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        try {
            final ItunesCallbackRequest itunesCallbackRequest = mapper.readValue(gson.toJson(callbackRequest.getBody()), ItunesCallbackRequest.class);
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
                    log.error(BaseLoggingMarkers.PAYMENT_ERROR, String.valueOf(e));
                }
            }
            return BaseResponse.<ChargingStatus>builder().body(ChargingStatus.builder().transactionStatus(finalTransactionStatus).build()).status(HttpStatus.OK).build();
        } catch (Exception e) {
            throw new WynkRuntimeException(WynkErrorType.UT999, e.getMessage());
        }
    }

    private void fetchAndUpdateFromReceipt(Transaction transaction) {
        String errorMessage = StringUtils.EMPTY;
        final String decodedReceipt = transaction.getValueFromPaymentMetaData(DECODED_RECEIPT);
        final ItunesReceiptType receiptType = ItunesReceiptType.getReceiptType(decodedReceipt);
        try {
            final List<LatestReceiptInfo> userLatestReceipts = getReceiptObjForUser(decodedReceipt, receiptType, transaction);
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
                    log.error(BaseLoggingMarkers.PAYMENT_ERROR, "Already have subscription for the corresponding iTunes id on another account");
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
                                .type(receiptType)
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

            if (transaction.getStatus() != TransactionStatus.SUCCESS) {
                transaction.setPaymentError(PaymentError.builder()
                        .description(errorMessage)
                        .build());
            }
        } catch (Exception e) {
            log.error(BaseLoggingMarkers.PAYMENT_ERROR, "fetchAndUpdateFromSource :: raised exception for uid : {} receipt : {} ", transaction.getUid(), decodedReceipt, e);
            throw new WynkRuntimeException(WynkErrorType.UT999, e, "Could not process iTunes validate transaction request for uid: " + transaction.getUid());
        }
    }

    private List<LatestReceiptInfo> getReceiptObjForUser(String receipt, ItunesReceiptType itunesReceiptType, Transaction transaction) {
        ItunesStatusCodes statusCode = ItunesStatusCodes.APPLE_21014;
        try {
            String encodedValue = itunesReceiptType.getEncodedItunesData(receipt);
            if (StringUtils.isBlank(encodedValue)) {
                statusCode = ItunesStatusCodes.APPLE_21011;
                log.error(BaseLoggingMarkers.PAYMENT_ERROR, "Encoded iTunes receipt data is empty! for iTunesData {}", receipt);
                throw new WynkRuntimeException(PaymentErrorType.PAY011, statusCode.getErrorTitle());
            }

            ResponseEntity<String> appStoreResponse;
            MerchantTransaction.MerchantTransactionBuilder merchantTransactionBuilder = MerchantTransaction.builder();

            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put(RECEIPT_DATA, encodedValue);
                requestJson.put(PASSWORD, itunesSecret);
                RequestEntity<String> requestEntity = new RequestEntity<>(requestJson.toJSONString(), HttpMethod.POST, URI.create(itunesApiUrl));
                appStoreResponse = restTemplate.exchange(requestEntity, String.class);
                merchantTransactionBuilder.request(gson.toJson(requestJson));
                merchantTransactionBuilder.response(gson.toJson(appStoreResponse));
            } catch (Exception e) {
                statusCode = ItunesStatusCodes.APPLE_21013;
                log.info("Exception while posting data to iTunes for uid {}, receipt {} ", transaction.getUid(), encodedValue);
                throw new WynkRuntimeException(PaymentErrorType.PAY011, e);
            }

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

            transaction.setMerchantTransaction(merchantTransactionBuilder.build());

            int status = Integer.parseInt(receiptObj.getStatus());
            ItunesStatusCodes responseITunesCode = ItunesStatusCodes.getItunesStatusCodes(status);
            if (status == 0) {
                return itunesReceiptType.getSubscriptionDetailJson(receiptObj);
            } else {
                if (responseITunesCode != null && failureCodes.contains(responseITunesCode)) {
                    statusCode = responseITunesCode;
                } else {
                    statusCode = ItunesStatusCodes.APPLE_21009;
                }
                log.error(BaseLoggingMarkers.PAYMENT_ERROR, "Failed to subscribe to iTunes: response {} request!! status : {} error {}", appStoreResponse, status, statusCode.getErrorTitle());
                throw new WynkRuntimeException(PaymentErrorType.PAY011, statusCode.getErrorTitle());
            }
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            transaction.setPaymentError(PaymentError.builder()
                    .code(statusCode.toString())
                    .description(statusCode.getErrorTitle())
                    .build());
            throw new WynkRuntimeException(WynkErrorType.UT999, e);
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

}
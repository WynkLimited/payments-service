package in.wynk.payment.service.impl;

import com.google.gson.Gson;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.Currency;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.CommonUtils;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.constant.StatusMode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.dto.apb.ApbConstants;
import in.wynk.payment.dto.apb.ApbStatus;
import in.wynk.payment.dto.apb.ApbTransaction;
import in.wynk.payment.dto.apb.ApbTransactionInquiryRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.Apb.ApbChargingStatusResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentCode.APB_GATEWAY;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APB_ERROR;
import static in.wynk.payment.dto.apb.ApbConstants.*;

@Slf4j
@Service(BeanConstant.APB_MERCHANT_PAYMENT_SERVICE)
public class APBMerchantPaymentService implements IRenewalMerchantPaymentService {

    @Value("${apb.callback.url}")
    private String CALLBACK_URL;
    @Value("${apb.merchant.id}")
    private String MERCHANT_ID;
    @Value("${apb.salt}")
    private String SALT;
    @Value("${apb.init.payment.url}")
    private String APB_INIT_PAYMENT_URL;
    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;
    @Value("${apb.txn.inquiry.url}")
    private String APB_TXN_INQUIRY_URL;
    @Value("${payment.pooling.queue.reconciliation.name}")
    private String reconciliationQueue;
    @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
    private int reconciliationMessageDelay;

    private final Gson gson;
    private final PaymentCachingService cachingService;
    private final ISQSMessagePublisher messagePublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManager;
    @Autowired
    @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE)
    private RestTemplate restTemplate;

    public APBMerchantPaymentService(Gson gson, PaymentCachingService cachingService, ISQSMessagePublisher messagePublisher, ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManager) {
        this.gson = gson;
        this.cachingService = cachingService;
        this.messagePublisher = messagePublisher;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }

    //TODO: use txn provided by payment manager and remove redundant code
    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        MultiValueMap<String, String> urlParameters = (MultiValueMap<String, String>) callbackRequest.getBody();

        String txnId = sessionDTO.get(SessionKeys.TRANSACTION_ID);
        String code = CommonUtils.getStringParameter(urlParameters, ApbConstants.CODE);
        String externalMessage = CommonUtils.getStringParameter(urlParameters, ApbConstants.MSG);
        String merchantId = CommonUtils.getStringParameter(urlParameters, ApbConstants.MID);
        String externalTxnId = CommonUtils.getStringParameter(urlParameters, ApbConstants.TRAN_ID);
        String amount = CommonUtils.getStringParameter(urlParameters, ApbConstants.TRAN_AMT);
        String txnDate = CommonUtils.getStringParameter(urlParameters, ApbConstants.TRAN_DATE);
        String requestHash = CommonUtils.getStringParameter(urlParameters, ApbConstants.HASH);
        ApbStatus status = ApbStatus.valueOf(CommonUtils.getStringParameter(urlParameters, ApbConstants.STATUS));
        String sessionId = SessionContextHolder.get().getId().toString();

        try {
            final Transaction transaction = transactionManager.get(txnId);
            if (verifyHash(status, merchantId, txnId, externalTxnId, amount, txnDate, code, requestHash)) {
                transactionManager.updateAndPublishSync(transaction, this::fetchAPBTxnStatus);
                if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                    return BaseResponse.redirectResponse(SUCCESS_PAGE + sessionId + SLASH + sessionDTO.get(OS));
                } else if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.error(PaymentLoggingMarker.APB_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at airtel payment bank end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY300);
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.error(PaymentLoggingMarker.APB_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at airtel payment bank end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY301);
                } else {
                    throw new PaymentRuntimeException(PaymentErrorType.PAY302);
                }
            } else {
                log.error(PaymentLoggingMarker.APB_CHARGING_CALLBACK_FAILURE,
                        "Invalid checksum found with transactionStatus: {}, Wynk transactionId: {}, PayU transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                        transaction.getStatus(),
                        transaction.getIdStr(),
                        externalTxnId,
                        code,
                        externalMessage,
                        transaction.getUid());
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, "invalid checksum is supplied for transactionId:" + transaction.getIdStr());
            }
        } catch (PaymentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Exception Occurred while verifying status from airtel payments bank");
        }
    }

    @Override
    public BaseResponse<Void> doCharging(ChargingRequest chargingRequest) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String msisdn = sessionDTO.get(MSISDN);
        final String uid = sessionDTO.get(UID);
        int planId = chargingRequest.getPlanId();
        PlanDTO planDTO = cachingService.getPlan(planId);
        double amount = planDTO.getFinalPrice();
        final PaymentEvent eventType = chargingRequest.isAutoRenew() ? PaymentEvent.SUBSCRIBE : PaymentEvent.PURCHASE;
        Transaction transaction = transactionManager.initiateTransaction(uid, msisdn, planId, amount, APB_GATEWAY, eventType);
        String apbRedirectURL = generateApbRedirectURL(transaction);
        return BaseResponse.redirectResponse(apbRedirectURL);
    }

    private <T> void publishSQSMessage(String queueName, int messageDelay, T message) {
        try {
            messagePublisher.publish(SendSQSMessageRequest.<T>builder()
                    .queueName(queueName)
                    .delaySeconds(messageDelay)
                    .message(message)
                    .build());
        } catch (Exception e) {
            throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }

    private String generateApbRedirectURL(Transaction transaction) {
        try {
            long txnDate = System.currentTimeMillis();
            String serviceName = ApbService.NB.name();
            String formattedDate = CommonUtils.getFormattedDate(txnDate, "ddMMyyyyHHmmss");
            String chargingUrl = getReturnUri(transaction, formattedDate, serviceName);
            return chargingUrl;
        } catch (Exception e) {
            throw new WynkRuntimeException(WynkErrorType.UT999, "Exception occurred while generating URL");
        }
    }

    private String getReturnUri(Transaction txn, String formattedDate, String serviceName) throws Exception {
        String sessionId = SessionContextHolder.get().getId().toString();
        String hashText = MERCHANT_ID + BaseConstants.HASH + txn.getIdStr() + BaseConstants.HASH + txn.getAmount() + BaseConstants.HASH + formattedDate + BaseConstants.HASH + serviceName + BaseConstants.HASH + SALT;
        String hash = CommonUtils.generateHash(hashText, SHA_512);
        return new URIBuilder(APB_INIT_PAYMENT_URL)
                .addParameter(MID, MERCHANT_ID)
                .addParameter(TXN_REF_NO, txn.getIdStr())
                .addParameter(SUCCESS_URL, getCallbackUrl(sessionId).toASCIIString())
                .addParameter(FAILURE_URL, getCallbackUrl(sessionId).toASCIIString())
                .addParameter(APB_AMOUNT, String.valueOf(txn.getAmount()))
                .addParameter(DATE, formattedDate)
                .addParameter(CURRENCY, Currency.INR.name())
                .addParameter(CUSTOMER_MOBILE, txn.getMsisdn())
                .addParameter(MERCHANT_NAME, BaseConstants.WYNK)
                .addParameter(ApbConstants.HASH, hash)
                .addParameter(SERVICE, serviceName)
                .build().toString();
    }

    private URI getCallbackUrl(String sid) throws URISyntaxException {
        return new URIBuilder(CALLBACK_URL + sid).build();
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionStatusRequest chargingStatusRequest) {
        Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId());
        ChargingStatusResponse status = ChargingStatusResponse.failure(chargingStatusRequest.getTransactionId(),transaction.getPlanId());
        if (chargingStatusRequest.getMode() == StatusMode.SOURCE) {
            transactionManager.updateAndPublishAsync(transaction, this::fetchAPBTxnStatus);
            status = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build();
        } else if (chargingStatusRequest.getMode() == StatusMode.LOCAL && TransactionStatus.SUCCESS.equals(transaction.getStatus())) {
            status = ChargingStatusResponse.success(transaction.getIdStr(), cachingService.validTillDate(chargingStatusRequest.getPlanId()), chargingStatusRequest.getPlanId());
        }
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(status).build();
    }


    public void fetchAPBTxnStatus(Transaction transaction) {
        String txnId = transaction.getId().toString();
        Builder builder = MerchantTransactionEvent.builder(txnId);
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        try {
            URI uri = new URI(APB_TXN_INQUIRY_URL);
            String txnDate = CommonUtils.getFormattedDate(transaction.getInitTime().getTimeInMillis(), "ddMMyyyyHHmmss");
            String hashText = MERCHANT_ID + BaseConstants.HASH + txnId + BaseConstants.HASH + transaction.getAmount() + BaseConstants.HASH + txnDate + BaseConstants.HASH + SALT;
            String hashValue = CommonUtils.generateHash(hashText, SHA_512);
            ApbTransactionInquiryRequest apbTransactionInquiryRequest = ApbTransactionInquiryRequest.builder()
                    .feSessionId(UUID.randomUUID().toString())
                    .txnRefNO(txnId).txnDate(txnDate)
                    .request(ECOMM_INQ).merchantId(MERCHANT_ID)
                    .hash(hashValue).langId(LANG_ID)
                    .amount(String.valueOf(transaction.getAmount()))
                    .build();
            String payload = gson.toJson(apbTransactionInquiryRequest);
            builder.request(payload);
            log.info("ApbTransactionInquiryRequest: {}", apbTransactionInquiryRequest);
            RequestEntity<String> requestEntity = new RequestEntity<>(payload, HttpMethod.POST, uri);
            ResponseEntity<ApbChargingStatusResponse> responseEntity = restTemplate.exchange(requestEntity, ApbChargingStatusResponse.class);
            ApbChargingStatusResponse apbChargingStatusResponse = responseEntity.getBody();
            if (Objects.nonNull(apbChargingStatusResponse) && CollectionUtils.isNotEmpty(apbChargingStatusResponse.getTxns())) {
                ApbTransaction apbTransaction = apbChargingStatusResponse.getTxns().get(0);
                if (StringUtils.equalsIgnoreCase(apbTransaction.getStatus(), ApbStatus.SUC.name())) {
                    finalTransactionStatus = TransactionStatus.SUCCESS;
                }
            }
            builder.response(apbChargingStatusResponse);
        } catch (HttpStatusCodeException e) {
            builder.response(e.getResponseBodyAsString());
            log.error(APB_ERROR, "Error for txnId {} from APB : {}", txnId, e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, "APB failure response - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error(APB_ERROR, "Error for txnId {} from APB : {}", txnId, e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, "Unable to fetch transaction status for txnId = " + txnId + "error- " + e.getMessage());
        } finally {
            transaction.setStatus(finalTransactionStatus.name());
            eventPublisher.publishEvent(builder.build());
        }
    }


    private boolean verifyHash(ApbStatus status, String merchantId, String txnId, String externalTxnId, String amount, String txnDate, String code, String requestHash) throws NoSuchAlgorithmException {
        String str = StringUtils.EMPTY;
        if (status == ApbStatus.SUC) {
            str = merchantId + BaseConstants.HASH + externalTxnId + BaseConstants.HASH + txnId + BaseConstants.HASH + amount + BaseConstants.HASH + txnDate + BaseConstants.HASH + SALT;
        } else if (status == ApbStatus.FAL) {
            str = merchantId + BaseConstants.HASH + txnId + BaseConstants.HASH + amount + BaseConstants.HASH + SALT + BaseConstants.HASH + code + "#FAL";
        }
        String generatedHash = CommonUtils.generateHash(str, SHA_512);
        return requestHash.equals(generatedHash);
    }

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        throw new UnsupportedOperationException("Unsupported operation - Renewal is not supported by APB");
    }

    public enum ApbService {
        NB("NetBanking"),
        WT("Wallet");

        String name;

        ApbService(String name) {
            this.name = name;
        }
    }

}

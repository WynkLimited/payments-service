package in.wynk.payment.service.impl;

import com.google.gson.Gson;
import in.wynk.commons.constants.Constants;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.Currency;
import in.wynk.commons.enums.PlanType;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.CommonUtils;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.ApbConstants;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.MerchantTransaction.MerchantTransactionBuilder;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.ApbTransaction;
import in.wynk.payment.dto.request.Apb.ApbTransactionInquiryRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.Apb.ApbChargingStatusResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.enums.Apb.ApbStatus;
import in.wynk.payment.enums.StatusMode;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Optional;
import java.util.UUID;

import static in.wynk.commons.constants.Constants.SERVICE;
import static in.wynk.commons.constants.Constants.*;
import static in.wynk.payment.constant.ApbConstants.HASH;
import static in.wynk.payment.constant.ApbConstants.*;
import static in.wynk.payment.core.constant.PaymentCode.APB_GATEWAY;
import static in.wynk.payment.core.constant.PaymentConstants.SESSION_ID;
import static in.wynk.payment.core.constant.PaymentConstants.TXN_ID;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APB_ERROR;

@Service(BeanConstant.APB_MERCHANT_PAYMENT_SERVICE)
public class APBMerchantPaymentService implements IRenewalMerchantPaymentService {
    private static final Logger logger = LoggerFactory.getLogger(APBMerchantPaymentService.class);

    @Autowired
    private RestTemplate restTemplate;

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

    @Value("${payment.failure.page}")
    private String FAILURE_PAGE;

    @Value("${apb.txn.inquiry.url}")
    private String APB_TXN_INQUIRY_URL;

    @Value("${payment.pooling.queue.reconciliation.name}")
    private String reconciliationQueue;

    @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
    private int reconciliationMessageDelay;

    @Autowired
    private ITransactionManagerService transactionManager;

    @Autowired
    private ISQSMessagePublisher messagePublisher;

    @Autowired
    private PaymentCachingService cachingService;

    @Autowired
    private Gson gson;

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        MultiValueMap<String, String> urlParameters = (MultiValueMap<String, String>) callbackRequest.getBody();
        ApbStatus status = ApbStatus.valueOf(CommonUtils.getStringParameter(urlParameters, ApbConstants.STATUS));
        String code = CommonUtils.getStringParameter(urlParameters, ApbConstants.CODE);
        String externalMessage = CommonUtils.getStringParameter(urlParameters, ApbConstants.MSG);
        String merchantId = CommonUtils.getStringParameter(urlParameters, ApbConstants.MID);
        String externalTxnId = CommonUtils.getStringParameter(urlParameters, ApbConstants.TRAN_ID);
        double amount = NumberUtils.toDouble(CommonUtils.getStringParameter(urlParameters, ApbConstants.TRAN_AMT));
        String txnDate = CommonUtils.getStringParameter(urlParameters, ApbConstants.TRAN_DATE);
        String txnId = CommonUtils.getStringParameter(urlParameters, ApbConstants.TXN_REF_NO);
        String requestHash = CommonUtils.getStringParameter(urlParameters, HASH);
        Transaction transaction = transactionManager.get(txnId);
        try {
            boolean verified = verifyHash(status, merchantId, txnId, externalTxnId, amount, txnDate, code, requestHash);
            String sessionId = SessionContextHolder.get().getId().toString();
            String url = String.format(FAILURE_PAGE, sessionId, txnId);
            if (verified && status == ApbStatus.SUC && fetchAPBTxnStatus(transaction, amount, txnDate).equals(TransactionStatus.SUCCESS)) {
                url = String.format(SUCCESS_PAGE, sessionId, txnId);
            }
            return BaseResponse.redirectResponse(url);
        } catch (Exception e) {
            throw new RuntimeException("Exception Occurred");
        } finally {
            transactionManager.upsert(transaction);
        }
    }

    @Override
    public BaseResponse<Void> doCharging(ChargingRequest chargingRequest) {
        String apbRedirectURL;
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String msisdn = sessionDTO.get(MSISDN);
        final String uid = sessionDTO.get(UID);
        final Double amount = sessionDTO.get(AMOUNT);
        final Integer planId = sessionDTO.get(PLAN_ID);
        final String wynkService = sessionDTO.get(SERVICE);

        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        final TransactionEvent eventType = selectedPlan.getPlanType() == PlanType.ONE_TIME_SUBSCRIPTION ? TransactionEvent.PURCHASE: TransactionEvent.SUBSCRIBE;

        Transaction transaction = transactionManager.initiateTransaction(uid, msisdn, planId, amount, APB_GATEWAY, eventType, wynkService);

        try {
            apbRedirectURL = generateApbRedirectURL(transaction.getId().toString());
        } finally {
            //Add reconciliation
            PaymentReconciliationMessage message = new PaymentReconciliationMessage(transaction);
            publishSQSMessage(reconciliationQueue, reconciliationMessageDelay, message);
        }
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

    private String generateApbRedirectURL(String txnId) {
        try {
            long txnDate = System.currentTimeMillis();
            String serviceName = ApbService.NB.name();
            String formattedDate = CommonUtils.getFormattedDate(txnDate, "ddMMyyyyHHmmss");
            return getReturnUri(txnId, formattedDate, serviceName);
        } catch (Exception e) {
            throw new WynkRuntimeException(WynkErrorType.UT999, "Exception occurred while generating URL");
        }
    }

    private String getReturnUri(String txnId, String formattedDate, String serviceName) throws Exception {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String sessionId = SessionContextHolder.get().getId().toString();
        Double amount = sessionDTO.get(AMOUNT);
        String msisdn = sessionDTO.get(MSISDN);
        String hashText = MERCHANT_ID + Constants.HASH + txnId + Constants.HASH + amount + Constants.HASH + formattedDate + Constants.HASH + serviceName + Constants.HASH + SALT;
        String hash = CommonUtils.generateHash(hashText, SHA_512);
        return new URIBuilder(APB_INIT_PAYMENT_URL)
                .addParameter(MID, MERCHANT_ID)
                .addParameter(TXN_REF_NO, txnId)
                .addParameter(SUCCESS_URL, getCallbackUrl(sessionId, txnId).toASCIIString())
                .addParameter(FAILURE_URL, getCallbackUrl(sessionId, txnId).toASCIIString())
                .addParameter(APB_AMOUNT, String.valueOf(amount))
                .addParameter(DATE, formattedDate)
                .addParameter(CURRENCY, Currency.INR.name())
                .addParameter(CUSTOMER_MOBILE, msisdn)
                .addParameter(MERCHANT_NAME, Constants.WYNK)
                .addParameter(HASH, hash)
                .addParameter(SERVICE, serviceName)
                .build().toString();
    }

    private URI getCallbackUrl(String sid, String txnId) throws URISyntaxException {
        return new URIBuilder(CALLBACK_URL).addParameter(SESSION_ID, sid).addParameter(TXN_ID, txnId).build();
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(ChargingStatusRequest chargingStatusRequest) {
        ChargingStatusResponse status = ChargingStatusResponse.failure();
        Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId());
        if (chargingStatusRequest.getMode() == StatusMode.SOURCE) {
            String txnDate = CommonUtils.getFormattedDate(transaction.getInitTime().getTimeInMillis(), "ddMMyyyyHHmmss");
            TransactionStatus txnStatus = fetchAPBTxnStatus(transaction, transaction.getAmount(), txnDate);
            status = ChargingStatusResponse.builder().transactionStatus(txnStatus).build();
        } else if (chargingStatusRequest.getMode() == StatusMode.LOCAL && TransactionStatus.SUCCESS.equals(transaction.getStatus())) {
            status = ChargingStatusResponse.success();
        }
        return new BaseResponse<>(status, HttpStatus.OK, null);
    }

    private TransactionStatus fetchAPBTxnStatus(Transaction transaction, double amount, String txnDate) {
        String txnId = transaction.getId().toString();
        MerchantTransactionBuilder merchantTxnBuilder = MerchantTransaction.builder();
        try {
            URI uri = new URI(APB_TXN_INQUIRY_URL);
            String hashText = MERCHANT_ID + Constants.HASH + txnId + Constants.HASH + amount + Constants.HASH + txnDate + Constants.HASH + SALT;
            String hashValue = CommonUtils.generateHash(hashText, SHA_512);
            ApbTransactionInquiryRequest apbTransactionInquiryRequest = ApbTransactionInquiryRequest.builder()
                    .feSessionId(UUID.randomUUID().toString())
                    .txnRefNo(txnId).txnDate(txnDate)
                    .request(ECOMM_INQ).merchantId(MERCHANT_ID)
                    .hash(hashValue).langId(LANG_ID)
                    .amount(String.valueOf(amount))
                    .build();
            String payload = gson.toJson(apbTransactionInquiryRequest);
            logger.info("ApbTransactionInquiryRequest: {}", apbTransactionInquiryRequest);
            merchantTxnBuilder.request(payload);
            RequestEntity<String> requestEntity = new RequestEntity<>(payload, HttpMethod.POST, uri);
            ApbChargingStatusResponse apbChargingStatusResponse;
            ResponseEntity<ApbChargingStatusResponse> responseEntity = restTemplate.exchange(requestEntity, ApbChargingStatusResponse.class);
            apbChargingStatusResponse = responseEntity.getBody();
            merchantTxnBuilder.response(gson.toJson(apbChargingStatusResponse));
            if (Objects.nonNull(apbChargingStatusResponse) && CollectionUtils.isNotEmpty(apbChargingStatusResponse.getTxns())) {
                Optional<ApbTransaction> apbTransaction = apbChargingStatusResponse.getTxns().stream().filter(txn -> StringUtils.equals(txnId, txn.getTxnId())).findAny();
                if (apbTransaction.isPresent() && StringUtils.equalsIgnoreCase(apbTransaction.get().getStatus(), ApbStatus.SUC.name())) {
                    transaction.setStatus(TransactionStatus.SUCCESS.name());
                    return TransactionStatus.SUCCESS;
                }
            }
            transaction.setStatus(TransactionStatus.FAILURE.name());
        } catch (HttpStatusCodeException e) {
            merchantTxnBuilder.response(e.getResponseBodyAsString());
            logger.error(APB_ERROR, "Error for txnId {} from APB : {}", txnId, e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, "APB failure response - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error(APB_ERROR, "Error for txnId {} from APB : {}", txnId, e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, "Unable to fetch transaction status for txnId = " + txnId + "error- " + e.getMessage());
        } finally {
            transaction.setMerchantTransaction(merchantTxnBuilder.build());
        }
        return TransactionStatus.FAILURE;
    }


    private boolean verifyHash(ApbStatus status, String merchantId, String txnId, String externalTxnId, Double amount, String txnDate, String code, String requestHash) throws NoSuchAlgorithmException {
        String str = StringUtils.EMPTY;
        if (status == ApbStatus.SUC) {
            str = merchantId + Constants.HASH + externalTxnId + Constants.HASH + txnId + Constants.HASH + amount + Constants.HASH + txnDate + Constants.HASH + SALT;
        } else if (status == ApbStatus.FAL) {
            str = merchantId + Constants.HASH + txnId + Constants.HASH + amount + Constants.HASH + SALT + Constants.HASH + code + "#FAL";
        }
        String generatedHash = CommonUtils.generateHash(str, SHA_512);
        return requestHash.equals(generatedHash);
    }

    @Override
    public BaseResponse<Void> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
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

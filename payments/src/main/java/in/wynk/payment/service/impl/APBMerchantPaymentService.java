package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.Currency;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.CommonUtils;
import in.wynk.common.utils.WynkResponseUtils;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.apb.*;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.apb.ApbChargingStatusResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.MERCHANT_ID;
import static in.wynk.payment.core.constant.PaymentConstants.MERCHANT_SECRET;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APB_ERROR;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.CALLBACK_PAYLOAD_PARSING_FAILURE;
import static in.wynk.payment.dto.apb.ApbConstants.CURRENCY;
import static in.wynk.payment.dto.apb.ApbConstants.*;

@Slf4j
@Service(BeanConstant.APB_MERCHANT_PAYMENT_SERVICE)
public class APBMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantPaymentCallbackService<AbstractCallbackResponse, ApbCallbackRequestPayload>, IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest> {

    @Value("${apb.init.payment.url}")
    private String APB_INIT_PAYMENT_URL;
    @Value("${apb.txn.inquiry.url}")
    private String APB_TXN_INQUIRY_URL;

    private final Gson gson;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManager;

    public APBMerchantPaymentService(Gson gson, ObjectMapper objectMapper, PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManager, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate template, IErrorCodesCacheService errorCodesCacheService) {
        super(cachingService, errorCodesCacheService);
        this.gson = gson;
        this.restTemplate = template;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }

    @Override
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback(ApbCallbackRequestPayload request) {
        final String code = request.getCode();
        final String merchantId = request.getMid();
        final String requestHash = request.getHash();
        final ApbStatus status = request.getStatus();
        final String externalMessage = request.getMsg();
        final String txnDate = request.getTransactionDate();
        final String amount = request.getTransactionAmount();
        final String externalTxnId = request.getTransactionId();
        try {
            final Transaction transaction = transactionManager.get(request.getTransactionId());
            if (verifyHash(transaction.getClientAlias(),status, merchantId, request.getTransactionId(), externalTxnId, amount, txnDate, code, requestHash)) {
                this.fetchAPBTxnStatus(transaction);
                if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                    return WynkResponseUtils.redirectResponse(((IChargingDetails) TransactionContext.getPurchaseDetails().get()).getPageUrlDetails().getSuccessPageUrl());
                } else if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.warn(PaymentLoggingMarker.APB_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at airtel payment bank end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY300);
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.warn(PaymentLoggingMarker.APB_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at airtel payment bank end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
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
    public ApbCallbackRequestPayload parseCallback(Map<String, Object> payload) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(payload), ApbCallbackRequestPayload.class);
        } catch (Exception e) {
            log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY006, e);
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingResponse> charge(AbstractChargingRequest<?> chargingRequest) {
        final String apbRedirectURL = generateApbRedirectURL((IChargingDetails) chargingRequest.getPurchaseDetails());
        return WynkResponseUtils.redirectResponse(apbRedirectURL);
    }

    private String generateApbRedirectURL(IChargingDetails chargingDetails) {
        try {
            long txnDate = System.currentTimeMillis();
            String serviceName = ApbService.NB.name();
            String formattedDate = CommonUtils.getFormattedDate(txnDate, "ddMMyyyyHHmmss");
            return getReturnUri(chargingDetails.getCallbackDetails().getCallbackUrl(), formattedDate, serviceName);
        } catch (Exception e) {
            throw new WynkRuntimeException(WynkErrorType.UT999, "Exception occurred while generating URL");
        }
    }

    private String getReturnUri(String callbackUrl, String formattedDate, String serviceName) throws Exception {
        Transaction txn = TransactionContext.get();
        String sessionId = SessionContextHolder.get().getId().toString();
        final String merchantId = PropertyResolverUtils.resolve(txn.getClientAlias(),BeanConstant.APB_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_ID);
        final String merchantSecret = PropertyResolverUtils.resolve(txn.getClientAlias(),BeanConstant.APB_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
        String hashText = merchantId + BaseConstants.HASH + txn.getIdStr() + BaseConstants.HASH + txn.getAmount() + BaseConstants.HASH + formattedDate + BaseConstants.HASH + serviceName + BaseConstants.HASH + merchantSecret;
        String hash = CommonUtils.generateHash(hashText, SHA_512);
        return new URIBuilder(APB_INIT_PAYMENT_URL)
                .addParameter(MID, merchantId)
                .addParameter(TXN_REF_NO, txn.getIdStr())
                .addParameter(SUCCESS_URL, callbackUrl)
                .addParameter(FAILURE_URL, callbackUrl)
                .addParameter(APB_AMOUNT, String.valueOf(txn.getAmount()))
                .addParameter(DATE, formattedDate)
                .addParameter(CURRENCY, Currency.INR.name())
                .addParameter(CUSTOMER_MOBILE, txn.getMsisdn())
                .addParameter(MERCHANT_NAME, WYNK)
                .addParameter(ApbConstants.HASH, hash)
                .addParameter(SERVICE, serviceName)
                .build().toString();
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = transactionManager.get(transactionStatusRequest.getTransactionId());
        this.fetchAPBTxnStatus(transaction);
        ChargingStatusResponse status = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build();
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(status).build();
    }


    public void fetchAPBTxnStatus(Transaction transaction) {
        String txnId = transaction.getId().toString();
        Builder builder = MerchantTransactionEvent.builder(txnId);
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        try {
            URI uri = new URI(APB_TXN_INQUIRY_URL);
            String txnDate = CommonUtils.getFormattedDate(transaction.getInitTime().getTimeInMillis(), "ddMMyyyyHHmmss");
            final String merchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(),BeanConstant.APB_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
            String hashText = MERCHANT_ID + BaseConstants.HASH + txnId + BaseConstants.HASH + transaction.getAmount() + BaseConstants.HASH + txnDate + BaseConstants.HASH + merchantSecret;
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

    private boolean verifyHash(String client,ApbStatus status, String merchantId, String txnId, String externalTxnId, String amount, String txnDate, String code, String requestHash) throws NoSuchAlgorithmException {
        String str = StringUtils.EMPTY;
        final String merchantSecret = PropertyResolverUtils.resolve(client,BeanConstant.APB_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
        if (status == ApbStatus.SUC) {
            str = merchantId + BaseConstants.HASH + externalTxnId + BaseConstants.HASH + txnId + BaseConstants.HASH + amount + BaseConstants.HASH + txnDate + BaseConstants.HASH + merchantSecret;
        } else if (status == ApbStatus.FAL) {
            str = merchantId + BaseConstants.HASH + txnId + BaseConstants.HASH + amount + BaseConstants.HASH + merchantSecret + BaseConstants.HASH + code + "#FAL";
        }
        String generatedHash = CommonUtils.generateHash(str, SHA_512);
        return requestHash.equals(generatedHash);
    }

    @Override
    public WynkResponseEntity<Void> doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        throw new UnsupportedOperationException("Unsupported operation - Renewal is not supported by APB");
    }

    @Getter
    public enum ApbService {
        NB("NetBanking"),
        WT("Wallet");

        final String name;

        ApbService(String name) {
            this.name = name;
        }
    }

}
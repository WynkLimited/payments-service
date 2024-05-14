package in.wynk.payment.gateway.payu.service;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.dto.payu.PayUChargingTransactionDetails;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.payu.PayUPreDebitNotification;
import in.wynk.payment.dto.response.payu.PayUPreDebitNotificationResponse;
import in.wynk.payment.dto.response.payu.PayURenewalResponse;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.IPreDebitNotificationService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY111;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_PRE_DEBIT_NOTIFICATION_ERROR;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_PRE_DEBIT_NOTIFICATION_SUCCESS;
import static in.wynk.payment.dto.payu.PayUConstants.*;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class PayUPreDebitGatewayServiceImpl implements IPreDebitNotificationService {
    private final Gson gson;
    private final ObjectMapper objectMapper;
    private final PaymentCachingService paymentCachingService;
    private final PayUCommonGateway payUCommonGateway;
    private final ITransactionManagerService transactionManagerService;
    private final IMerchantTransactionService merchantTransactionService;

    public PayUPreDebitGatewayServiceImpl (Gson gson, ObjectMapper objectMapper, PaymentCachingService paymentCachingService, PayUCommonGateway payUCommonGateway,
                                           ITransactionManagerService transactionManagerService,
                                           IMerchantTransactionService merchantTransactionService) {
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.paymentCachingService = paymentCachingService;
        this.payUCommonGateway = payUCommonGateway;
        this.transactionManagerService = transactionManagerService;
        this.merchantTransactionService = merchantTransactionService;
    }

    @Override
    public AbstractPreDebitNotificationResponse notify (PreDebitNotificationMessage message) {
        try {
            LinkedHashMap<String, Object> orderedMap = new LinkedHashMap<>();
            String txnId = message.getTransactionId();
            if (StringUtils.isNotBlank(message.getInitialTransactionId())) {
                txnId = message.getInitialTransactionId();
            }
            Transaction transaction = transactionManagerService.get(txnId);
            MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(txnId);
            if (Objects.nonNull(merchantTransaction)) {
                PayURenewalResponse payURenewalResponse = objectMapper.convertValue(merchantTransaction.getResponse(), PayURenewalResponse.class);
                PayUChargingTransactionDetails transactionDetails = payURenewalResponse.getTransactionDetails().get(txnId);
                UUID requestId = UUIDs.timeBased();
                if (payUCommonGateway.validateMandateStatus(transaction, requestId.toString(), transactionDetails, false)) {
                    orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, merchantTransaction.getExternalTransactionId());
                    orderedMap.put(PAYU_REQUEST_ID, requestId);
                    orderedMap.put(PAYU_DEBIT_DATE, message.getDate());
                    orderedMap.put(PAYU_INVOICE_DISPLAY_NUMBER, message.getTransactionId());
                    orderedMap.put(PAYU_TRANSACTION_AMOUNT, paymentCachingService.getPlan(transaction.getPlanId()).getFinalPrice());
                    String variable = gson.toJson(orderedMap);
                    MultiValueMap<String, String> requestMap = payUCommonGateway.buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.PRE_DEBIT_SI.getCode(), variable);
                    PayUPreDebitNotificationResponse response = payUCommonGateway.exchange(payUCommonGateway.INFO_API, requestMap, new TypeReference<PayUPreDebitNotificationResponse>() {
                    });
                    if (response.getStatus().equalsIgnoreCase(INTEGER_VALUE)) {
                        log.info(PAYU_PRE_DEBIT_NOTIFICATION_SUCCESS, "invoiceId: " + response.getInvoiceId() + " invoiceStatus: " + response.getInvoiceStatus());
                    } else {
                        throw new WynkRuntimeException(PAY111, response.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof WynkRuntimeException) {
                throw e;
            }
            log.error(PAYU_PRE_DEBIT_NOTIFICATION_ERROR, e.getMessage());
            throw new WynkRuntimeException(PAY111);
        }
        return PayUPreDebitNotification.builder().tid(message.getTransactionId()).transactionStatus(TransactionStatus.SUCCESS).build();
    }
}

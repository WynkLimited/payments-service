package in.wynk.payment.gateway.payu.service;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.CardConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PreDebitRequest;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.dto.payu.PayUChargingTransactionDetails;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.payu.PayUPreDebitNotification;
import in.wynk.payment.dto.response.payu.PayUPreDebitNotificationResponse;
import in.wynk.payment.dto.response.payu.PayURenewalResponse;
import in.wynk.payment.service.IPreDebitNotificationService;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MultiValueMap;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;

import static in.wynk.payment.core.constant.PaymentErrorType.PAYU007;
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
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final RecurringTransactionUtils recurringTransactionUtils;

    public PayUPreDebitGatewayServiceImpl (Gson gson, ObjectMapper objectMapper, PaymentCachingService paymentCachingService, PayUCommonGateway payUCommonGateway,
                                           ITransactionManagerService transactionManagerService, IRecurringPaymentManagerService recurringPaymentManagerService,
                                           RecurringTransactionUtils recurringTransactionUtils) {
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.paymentCachingService = paymentCachingService;
        this.payUCommonGateway = payUCommonGateway;
        this.transactionManagerService = transactionManagerService;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
        this.recurringTransactionUtils = recurringTransactionUtils;
    }

    @Override
    public AbstractPreDebitNotificationResponse notify (PreDebitRequest request) {

        LinkedHashMap<String, Object> orderedMap = new LinkedHashMap<>();
        PaymentRenewal lastRenewal = recurringPaymentManagerService.getRenewalById(request.getTransactionId());
        String txnId = payUCommonGateway.getUpdatedTransactionId(request.getTransactionId(), lastRenewal);
        MerchantTransaction merchantTransaction = payUCommonGateway.getMerchantData(txnId);
        if (merchantTransaction == null) {
            throw new WynkRuntimeException("Merchant data is null");
        }
        Transaction transaction = transactionManagerService.get(request.getTransactionId());
        // check eligibility for renewal
        if (recurringTransactionUtils.isEligibleForRenewal(transaction, true)) {
            PayUChargingTransactionDetails payUChargingTransactionDetails =
                    objectMapper.convertValue(merchantTransaction.getResponse(), PayURenewalResponse.class).getTransactionDetails().get(request.getTransactionId());
            String mode = payUChargingTransactionDetails.getMode();
            // check mandate status
            boolean isMandateActive = payUCommonGateway.validateMandateStatus(transaction, payUChargingTransactionDetails, mode,false);
            if (isMandateActive) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, merchantTransaction.getExternalTransactionId());
                orderedMap.put(PAYU_REQUEST_ID, UUIDs.timeBased());
                orderedMap.put(PAYU_DEBIT_DATE, format.format(request.getDay().getTime()));
                if(CardConstants.CREDIT_CARD.equals(mode) || CardConstants.DEBIT_CARD.equals(mode) || CardConstants.SI.equals(mode)) {
                    orderedMap.put(PAYU_INVOICE_DISPLAY_NUMBER, request.getTransactionId());
                }
                orderedMap.put(PAYU_TRANSACTION_AMOUNT, paymentCachingService.getPlan(transaction.getPlanId()).getFinalPrice());
                String variable = gson.toJson(orderedMap);
                MultiValueMap<String, String> requestMap = payUCommonGateway.buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.PRE_DEBIT_SI.getCode(), variable);
                try {
                    PayUPreDebitNotificationResponse response = payUCommonGateway.exchange(payUCommonGateway.INFO_API, requestMap, new TypeReference<PayUPreDebitNotificationResponse>() {
                    });
                    if (response.getStatus().equalsIgnoreCase(INTEGER_VALUE)) {
                        log.info(PAYU_PRE_DEBIT_NOTIFICATION_SUCCESS, "invoiceId: " + response.getInvoiceId() + " invoiceStatus: " + response.getInvoiceStatus());
                    } else {
                        throw new WynkRuntimeException(PAYU007, response.getMessage());
                    }


                            Calendar calendar = Calendar.getInstance();
                           // int day = calendar.get(Calendar.DAY_OF_MONTH);
                           // int hour = calendar.get(Calendar.HOUR_OF_DAY);
                            calendar.add(Calendar.DAY_OF_MONTH, 2);

                            if(request.getRenewalDay().compareTo(calendar) < 0) {
                                int day = calendar.get(Calendar.DAY_OF_MONTH);
                                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                                recurringPaymentManagerService.updateRenewalSchedule();
                            }

                    return PayUPreDebitNotification.builder().tid(request.getTransactionId()).transactionStatus(TransactionStatus.SUCCESS).build();
                } catch (Exception e) {
                    log.error(PAYU_PRE_DEBIT_NOTIFICATION_ERROR, e.getMessage());
                    if (e instanceof WynkRuntimeException) {
                        throw e;
                    }
                    throw new WynkRuntimeException(PAYU007);
                }
            }
            AnalyticService.update("mandateStatus", "INACTIVE");
        }
        return null;
    }
}

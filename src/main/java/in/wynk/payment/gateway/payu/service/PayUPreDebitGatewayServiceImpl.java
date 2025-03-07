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
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.dto.payu.PayUChargingTransactionDetails;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.payu.PayUPreDebitNotification;
import in.wynk.payment.dto.response.payu.PayUPreDebitNotificationResponse;
import in.wynk.payment.dto.response.payu.PayURenewalResponse;
import in.wynk.payment.service.IPreDebitNotificationService;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MultiValueMap;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentErrorType.PAYU007;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_PRE_DEBIT_NOTIFICATION_ERROR;
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
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final RecurringTransactionUtils recurringTransactionUtils;

    public PayUPreDebitGatewayServiceImpl (Gson gson, ObjectMapper objectMapper, PaymentCachingService paymentCachingService, PayUCommonGateway payUCommonGateway,
                                           IRecurringPaymentManagerService recurringPaymentManagerService,
                                           RecurringTransactionUtils recurringTransactionUtils) {
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.paymentCachingService = paymentCachingService;
        this.payUCommonGateway = payUCommonGateway;
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
        Transaction transaction = TransactionContext.get();
        // check eligibility for renewal
        if (recurringTransactionUtils.isEligibleForRenewal(transaction, true)) {
            PayUChargingTransactionDetails payUChargingTransactionDetails =
                    objectMapper.convertValue(merchantTransaction.getResponse(), PayURenewalResponse.class).getTransactionDetails().get(request.getTransactionId());
            String mode = payUChargingTransactionDetails.getMode();
            // check mandate status
            boolean isMandateActive = payUCommonGateway.validateMandateStatus(transaction, payUChargingTransactionDetails, mode, false);
            if (isMandateActive) {
                try {
                    Calendar cal = Calendar.getInstance();
                    boolean renewalUpdateRequired = findRenewalRequiredAndUpdateTime(cal, request);
                    if (CardConstants.CREDIT_CARD.equals(mode) || CardConstants.DEBIT_CARD.equals(mode) || CardConstants.SI.equals(mode)) {
                        orderedMap.put(PAYU_INVOICE_DISPLAY_NUMBER, request.getTransactionId());
                    }
                    orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, merchantTransaction.getExternalTransactionId());
                    orderedMap.put(PAYU_REQUEST_ID, UUIDs.timeBased());
                    orderedMap.put(PAYU_DEBIT_DATE, new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime()));
                    orderedMap.put(PAYU_TRANSACTION_AMOUNT, paymentCachingService.getPlan(transaction.getPlanId()).getFinalPrice());
                    String variable = gson.toJson(orderedMap);
                    MultiValueMap<String, String> requestMap = payUCommonGateway.buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.PRE_DEBIT_SI.getCode(), variable);

                    PayUPreDebitNotificationResponse response = payUCommonGateway.exchange("https://infoinfo.payu.in/merchant/postservice.php?form=2", requestMap, new TypeReference<PayUPreDebitNotificationResponse>() {
                    });
                    if (response.getStatus().equalsIgnoreCase(INTEGER_VALUE) && !Objects.equals(request.getUid(), "WY80bpcN4JO_2DTfD0")) {
                        AnalyticService.update("PAYU_PRE_DEBIT_NOTIFICATION_SUCCESS", String.valueOf(response));
                        if (renewalUpdateRequired) {
                            recurringPaymentManagerService.updateRenewalSchedule(request.getClientAlias(), request.getTransactionId(), cal, cal.getTime());
                        }
                    } else {
                        throw new WynkRuntimeException(PAYU007, response.getMessage());
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

    private boolean findRenewalRequiredAndUpdateTime (Calendar cal, PreDebitRequest request) {
        boolean renewalUpdateRequired = false;
        try {
            cal.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(request.getRenewalDay() + " " + request.getRenewalHour()));
            Calendar date = Calendar.getInstance();
            date.add(Calendar.DAY_OF_MONTH, 1);
            long diffInMillies = date.getTimeInMillis() - cal.getTimeInMillis();
            //if date in database id more than 24 hours from now, no update in db required
            if (diffInMillies < 0) {
                return false;
            }
            double diffInHours = diffInMillies / 3600000.0;
            int requiredHours = ((int) Math.ceil(diffInHours)) + 1;
            cal.add(Calendar.HOUR_OF_DAY, requiredHours);
            renewalUpdateRequired = true;

        } catch (ParseException e) {
            log.error("Exception occurred while parsing the date", e);
        }
        return renewalUpdateRequired;
    }
}

package in.wynk.payment.gateway.payu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.callback.DefaultPaymentCallbackResponse;
import in.wynk.payment.dto.payu.*;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY006;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;

@Slf4j
public class PayUCallbackGatewayService implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> {

    private final PayUCommonGatewayService common;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> callbackHandler;

    public PayUCallbackGatewayService (PayUCommonGatewayService common, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.common = common;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.callbackHandler = new DelegatePayUCallbackHandler();
    }

    @Override
    public AbstractPaymentCallbackResponse handleCallback(PayUCallbackRequestPayload callbackRequest) {
        return callbackHandler.handleCallback(callbackRequest);
    }

    @Override
    public PayUCallbackRequestPayload parseCallback(Map<String, Object> payload) {
        return callbackHandler.parseCallback(payload);
    }

    private class DelegatePayUCallbackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> {

        private final Map<Class<? extends IPaymentCallback<? extends AbstractPaymentCallbackResponse, ? extends PayUCallbackRequestPayload>>, IPaymentCallback> delegator = new HashMap<>();

        public DelegatePayUCallbackHandler() {
            this.delegator.put(GenericPayUCallbackHandler.class, new GenericPayUCallbackHandler());
            this.delegator.put(RefundPayUCallBackHandler.class, new RefundPayUCallBackHandler());
        }

        public boolean validate(PayUCallbackRequestPayload callbackRequest) {
            final Transaction transaction = TransactionContext.get();
            final String transactionId = transaction.getIdStr();
            final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
            final String payUMerchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
            return validateCallbackChecksum(payUMerchantKey, payUMerchantSecret, transactionId, callbackRequest.getStatus(), callbackRequest.getUdf(), callbackRequest.getEmail(), callbackRequest.getFirstName(), String.valueOf(transaction.getPlanId()), transaction.getAmount(), callbackRequest.getResponseHash());
        }

        @Override
        public AbstractPaymentCallbackResponse handleCallback(PayUCallbackRequestPayload callbackRequest) {
            final Transaction transaction = TransactionContext.get();
            final String transactionId = transaction.getIdStr();
            try {
                final String errorCode = callbackRequest.getError();
                final String errorMessage = callbackRequest.getErrorMessage();
                final IPaymentCallback callbackService = delegator.get(PayUAutoRefundCallbackRequestPayload.class.isAssignableFrom(callbackRequest.getClass()) ? RefundPayUCallBackHandler.class : GenericPayUCallbackHandler.class);
                if (validate(callbackRequest)) {
                    return callbackService.handleCallback(callbackRequest);
                } else {
                    log.error(PAYU_CHARGING_CALLBACK_FAILURE, "Invalid checksum found with transactionStatus: {}, Wynk transactionId: {}, PayU transactionId: {}, Reason: error code: {}, error message: {} for uid: {}", callbackRequest.getStatus(), transactionId, callbackRequest.getExternalTransactionId(), errorCode, errorMessage, transaction.getUid());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found with transaction id:" + transactionId);
                }
            } catch (Exception e) {
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
            }
        }

        @Override
        public PayUCallbackRequestPayload parseCallback(Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return objectMapper.readValue(json, PayUCallbackRequestPayload.class);
            } catch (Exception e) {
                log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
                throw new WynkRuntimeException(PAY006, e);
            }
        }

        private class GenericPayUCallbackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> {

            @Override
            public AbstractPaymentCallbackResponse handleCallback(PayUCallbackRequestPayload callbackRequest) {
                final Transaction transaction = TransactionContext.get();
                common.syncChargingTransactionFromSource(transaction);
                if (!EnumSet.of(PaymentEvent.RENEW, PaymentEvent.REFUND).contains(transaction.getType())) {
                    Optional<IPurchaseDetails> optionalDetails = TransactionContext.getPurchaseDetails();
                    if (optionalDetails.isPresent()) {
                        final String redirectionUrl;
                        IChargingDetails chargingDetails = (IChargingDetails) optionalDetails.get();
                        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                            redirectionUrl = chargingDetails.getPageUrlDetails().getPendingPageUrl();
                        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                            redirectionUrl = chargingDetails.getPageUrlDetails().getUnknownPageUrl();
                        } else if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                            redirectionUrl = chargingDetails.getPageUrlDetails().getSuccessPageUrl();
                        } else {
                            redirectionUrl = chargingDetails.getPageUrlDetails().getFailurePageUrl();
                        }
                        return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).redirectUrl(redirectionUrl).build();
                    }
                }
                return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
            }
        }

        private class RefundPayUCallBackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUAutoRefundCallbackRequestPayload> {

            @Override
            public AbstractPaymentCallbackResponse handleCallback(PayUAutoRefundCallbackRequestPayload callbackRequest) {
                final Transaction transaction = TransactionContext.get();
                common.syncRefundTransactionFromSource(transaction, callbackRequest.getRequestId());
                // if an auto refund transaction is successful after recon from payu then transaction status should be marked as auto refunded
                if (transaction.getStatus() == TransactionStatus.SUCCESS)
                    transaction.setStatus(TransactionStatus.AUTO_REFUND.getValue());
                return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
            }


        }
    }

    /**
     * Callback Flow ->
     * 1. Validate checksum
     * 2. Sync Charging transaction
     * 3. Return redirection URL from transaction
     **/
    /*@Override
    public AbstractPaymentCallbackResponse handleCallback(PayUCallbackRequestPayload request) {
        handleCallbackInternal(request);
        final Transaction transaction = TransactionContext.get();
        if (!EnumSet.of(PaymentEvent.RENEW, PaymentEvent.REFUND).contains(transaction.getType())) {
            Optional<IPurchaseDetails> optionalDetails = TransactionContext.getPurchaseDetails();
            if (optionalDetails.isPresent()) {
                final String redirectionUrl;
                IChargingDetails chargingDetails = (IChargingDetails) optionalDetails.get();
                if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    redirectionUrl = chargingDetails.getPageUrlDetails().getPendingPageUrl();
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    redirectionUrl = chargingDetails.getPageUrlDetails().getUnknownPageUrl();
                } else if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    redirectionUrl = chargingDetails.getPageUrlDetails().getSuccessPageUrl();
                } else {
                    redirectionUrl = chargingDetails.getPageUrlDetails().getFailurePageUrl();
                }
                return DefaultPaymentCallbackResponse.builder().redirectUrl(redirectionUrl).build();
            }
        }
        return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
    }

    @Override
    public PayUCallbackRequestPayload parseCallback(Map<String, Object> payload) {
        try {
            return gson.fromJson(gson.toJsonTree(payload), PayUCallbackRequestPayload.class);
        } catch (Exception e) {
            log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PAY006, e);
        }
    }

    private void handleCallbackInternal(PayUCallbackRequestPayload payUCallbackRequestPayload) {
        final Transaction transaction = TransactionContext.get();
        final String transactionId = transaction.getIdStr();
        try {
            final String errorCode = payUCallbackRequestPayload.getError();
            final String errorMessage = payUCallbackRequestPayload.getErrorMessage();
            final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
            final String payUMerchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
            final boolean isValidHash = validateCallbackChecksum(payUMerchantKey, payUMerchantSecret, transactionId,
                    payUCallbackRequestPayload.getStatus(),
                    payUCallbackRequestPayload.getUdf1(),
                    payUCallbackRequestPayload.getEmail(),
                    payUCallbackRequestPayload.getFirstName(),
                    String.valueOf(transaction.getPlanId()),
                    transaction.getAmount(),
                    payUCallbackRequestPayload.getResponseHash());

            if (isValidHash) {
                common.syncChargingTransactionFromSource(transaction);
            } else {
                log.error(PAYU_CHARGING_CALLBACK_FAILURE,
                        "Invalid checksum found with transactionStatus: {}, Wynk transactionId: {}, PayU transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                        payUCallbackRequestPayload.getStatus(),
                        transactionId,
                        payUCallbackRequestPayload.getExternalTransactionId(),
                        errorCode,
                        errorMessage,
                        transaction.getUid());
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found with transaction id:" + transactionId);
            }
        } catch (PaymentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        }
    }*/

    private boolean validateCallbackChecksum(String payUMerchantKey, String payUSalt, String transactionId, String transactionStatus, String udf1, String email, String firstName, String planTitle, double amount, String payUResponseHash) {
        DecimalFormat df = new DecimalFormat("#.00");
        String generatedString =
                payUSalt + PIPE_SEPARATOR + transactionStatus + "||||||" + udf1 + PIPE_SEPARATOR + email + PIPE_SEPARATOR
                        + firstName + PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + df.format(amount) + PIPE_SEPARATOR + transactionId
                        + PIPE_SEPARATOR + payUMerchantKey;
        final String generatedHash = EncryptionUtils.generateSHA512Hash(generatedString);
        assert generatedHash != null;
        return generatedHash.equals(payUResponseHash);
    }
}

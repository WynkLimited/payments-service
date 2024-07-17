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
import in.wynk.payment.dto.aps.common.WebhookConfigType;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.callback.DefaultPaymentCallbackResponse;
import in.wynk.payment.dto.payu.*;
import in.wynk.payment.dto.response.payu.PayUVerificationResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.payment.utils.RecurringTransactionUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY006;
import static in.wynk.payment.core.constant.PaymentErrorType.PAYU009;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;

@Slf4j
public class PayUCallbackGatewayImpl implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> {

    private final PayUCommonGateway common;
    private final ObjectMapper objectMapper;
    private final IMerchantTransactionService merchantTransactionService;
    private final RecurringTransactionUtils recurringTransactionUtils;
    private final Map<String, IPaymentCallback<? extends AbstractPaymentCallbackResponse, ? extends PayUCallbackRequestPayload>> delegator = new HashMap<>();

    public PayUCallbackGatewayImpl (PayUCommonGateway common, ObjectMapper objectMapper, IMerchantTransactionService merchantTransactionService, RecurringTransactionUtils recurringTransactionUtils) {
        this.common = common;
        this.objectMapper = objectMapper;
        this.recurringTransactionUtils = recurringTransactionUtils;
        this.merchantTransactionService = merchantTransactionService;
        this.delegator.put(WebhookConfigType.PAYMENT_STATUS.name(), new PayUCallbackGatewayImpl.GenericPayUCallbackHandler());
        this.delegator.put(WebhookConfigType.REFUND_STATUS.name(), new PayUCallbackGatewayImpl.RefundPayUCallBackHandler());
        this.delegator.put(WebhookConfigType.MANDATE_STATUS.name(), new PayUCallbackGatewayImpl.MandateStatusPayUCallBackHandler());
    }

    @Override
    public AbstractPaymentCallbackResponse handle (PayUCallbackRequestPayload request) {
        final Transaction transaction = TransactionContext.get();
        final String transactionId = transaction.getIdStr();
        try {
            final String errorCode = request.getError();
            final String errorMessage = request.getErrorMessage();
            final IPaymentCallback callbackService = delegator.get(request.getAction());
            if (isValid(request)) {
                return callbackService.handle(request);
            } else {
                log.error(PAYU_CHARGING_CALLBACK_FAILURE,
                        "Invalid checksum found with transactionStatus: {}, Wynk transactionId: {}, PayU transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                        request.getStatus(), transactionId, request.getExternalTransactionId(), errorCode, errorMessage, transaction.getUid());
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found with transaction id:" + transactionId);
            }
        } catch (Exception e) {
            throw new PaymentRuntimeException(PaymentErrorType.PAYU010, e);
        }
    }

    private boolean isValid (PayUCallbackRequestPayload callbackRequest) {
        final Transaction transaction = TransactionContext.get();
        final String transactionId = transaction.getIdStr();
        final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        if (callbackRequest instanceof PayuRealtimeMandatePayload) {
            PayuRealtimeMandatePayload request = (PayuRealtimeMandatePayload) callbackRequest;
            if (Objects.nonNull(request.getNotificationType())) {
                final String generatedString =
                        request.getStatus() + PIPE_SEPARATOR + request.getAuthPayuId() + PIPE_SEPARATOR + request.getNotificationType().toString() + PIPE_SEPARATOR +
                                request.getSiDetails().getBillingAmount() +
                                PIPE_SEPARATOR + request.getSiDetails().getPaymentStartDate() + PIPE_SEPARATOR + request.getSiDetails().getPaymentEndDate() + PIPE_SEPARATOR + request.getMessage() +
                                PIPE_SEPARATOR + request.getEventDate() + PIPE_SEPARATOR + request.getKey() + PIPE_SEPARATOR + request.getUdf1() + PIPE_SEPARATOR + request.getUdf2() + PIPE_SEPARATOR +
                                request.getUdf3() + PIPE_SEPARATOR + request.getUdf4() + PIPE_SEPARATOR + request.getUdf5() + PIPE_SEPARATOR + payUMerchantSecret;
                return validateHashEquality(generatedString, request.getResponseHash());
            } else {
                final String generatedString =
                        request.getStatus() + PIPE_SEPARATOR + request.getAction() + PIPE_SEPARATOR + request.getAuthPayuId() + PIPE_SEPARATOR + request.getDateTime() + PIPE_SEPARATOR +
                                request.getAmount() + PIPE_SEPARATOR + request.getEndDate() + payUMerchantSecret;
                return validateHashEquality(generatedString, request.getResponseHash());
            }

        }
        return validateCallbackChecksum(payUMerchantKey, payUMerchantSecret, transactionId, callbackRequest.getStatus(), callbackRequest.getUdf(), callbackRequest.getEmail(),
                callbackRequest.getFirstName(), ((transaction.getType() == PaymentEvent.POINT_PURCHASE) ? transaction.getItemId() : String.valueOf(transaction.getPlanId())), transaction.getAmount(),
                callbackRequest.getResponseHash());
    }

    private boolean validateHashEquality (String generatedString, String payUResponseHash) {
        final String generatedHash = EncryptionUtils.generateSHA512Hash(generatedString);
        assert generatedHash != null;
        return generatedHash.equals(payUResponseHash);
    }

    @Override
    public PayUCallbackRequestPayload parse (Map<String, Object> payload) {
        try {
            Object notificationType = payload.get("notificationType");
            Object action = payload.get("action");
            String type;
            if (Objects.nonNull(notificationType) || Objects.nonNull(action)) {
                type = WebhookConfigType.MANDATE_STATUS.name();
                String txnId = merchantTransactionService.findTransactionIdByExternalTransactionId(payload.get("authpayuid").toString());
                payload.put("transactionId", txnId);
            } else if (Objects.nonNull(payload.get("action")) && payload.get("action").equals("refund")) {
                type = WebhookConfigType.REFUND_STATUS.name();
            } else {
                type = WebhookConfigType.PAYMENT_STATUS.name();
            }

            return delegator.get(type).parse(payload);
        } catch (Exception e) {
            log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PAY006, e);
        }
    }

    private class GenericPayUCallbackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> {

        @Override
        public AbstractPaymentCallbackResponse handle (PayUCallbackRequestPayload request) {
            final Transaction transaction = TransactionContext.get();
            common.syncTransactionWithSourceResponse(transaction, PayUVerificationResponse.<PayUChargingTransactionDetails>builder().status(1)
                    .transactionDetails(Collections.singletonMap(transaction.getIdStr(), AbstractPayUTransactionDetails.from(request))).build());
            if (!EnumSet.of(PaymentEvent.RENEW, PaymentEvent.REFUND).contains(transaction.getType())) {
                Optional<IPurchaseDetails> optionalDetails = TransactionContext.getPurchaseDetails();
                if (optionalDetails.isPresent()) {
                    final String redirectionUrl;
                    IChargingDetails chargingDetails = (IChargingDetails) optionalDetails.get();
                    if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                        log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(),
                                transaction.getId().toString());
                        redirectionUrl = chargingDetails.getPageUrlDetails().getPendingPageUrl();
                    } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                        log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
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

        @Override
        public PayUCallbackRequestPayload parse (Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return objectMapper.readValue(json, PayUCallbackRequestPayload.class);
            } catch (Exception e) {
                throw new WynkRuntimeException(PAYU009, e);
            }
        }
    }

    private class RefundPayUCallBackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUAutoRefundCallbackRequestPayload> {

        @Override
        public AbstractPaymentCallbackResponse handle (PayUAutoRefundCallbackRequestPayload request) {
            final Transaction transaction = TransactionContext.get();
            common.syncTransactionWithSourceResponse(transaction, PayUVerificationResponse.<PayUChargingTransactionDetails>builder().status(1)
                    .transactionDetails(Collections.singletonMap(transaction.getIdStr(), AbstractPayUTransactionDetails.from(request))).build());
            if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                transaction.setStatus(TransactionStatus.AUTO_REFUND.getValue());
            }
            return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
        }

        @Override
        public PayUAutoRefundCallbackRequestPayload parse (Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return objectMapper.readValue(json, PayUAutoRefundCallbackRequestPayload.class);
            } catch (Exception e) {
                throw new WynkRuntimeException(PAYU009, e);
            }
        }

    }

    private class MandateStatusPayUCallBackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, PayuRealtimeMandatePayload> {
        @Override
        public AbstractPaymentCallbackResponse handle (PayuRealtimeMandatePayload callbackRequest) {
            final Transaction transaction = TransactionContext.get();
            String description = "Mandate is already " + callbackRequest.getStatus();
            recurringTransactionUtils.cancelRenewalBasedOnRealtimeMandate(description, transaction);
            transaction.setStatus(TransactionStatus.CANCELLED.getValue());
            return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
        }

        @Override
        public PayuRealtimeMandatePayload parse (Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return objectMapper.readValue(json, PayuRealtimeMandatePayload.class);
            } catch (Exception e) {
                throw new WynkRuntimeException(PAYU009, e);
            }

        }
    }

    private boolean validateCallbackChecksumForRealtimeMandate (String payUMerchantKey, String payUMerchantSecret, PayuRealtimeMandatePayload request) {
        return false;
    }

    @SneakyThrows
    private boolean validateCallbackChecksum (String payUMerchantKey, String payUSalt, String transactionId, String transactionStatus, String udf1, String email, String firstName, String planTitle,
                                              double amount, String payUResponseHash) {
        final DecimalFormat df = new DecimalFormat("#0.00");
        final String generatedString =
                payUSalt + PIPE_SEPARATOR + transactionStatus + "||||||" + udf1 + PIPE_SEPARATOR + URLDecoder.decode(email, String.valueOf(StandardCharsets.UTF_8)) + PIPE_SEPARATOR + firstName +
                        PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + df.format(amount) + PIPE_SEPARATOR + transactionId + PIPE_SEPARATOR + payUMerchantKey;
        return validateHashEquality(generatedString, payUResponseHash);

    }
}

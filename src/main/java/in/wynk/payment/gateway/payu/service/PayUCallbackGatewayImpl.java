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
import in.wynk.payment.dto.response.payu.PayUVerificationResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY006;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;

@Slf4j
public class PayUCallbackGatewayImpl implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> {

    private final PayUCommonGateway common;
    private final ObjectMapper objectMapper;
    private final IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> callbackHandler;
    private final ApplicationEventPublisher eventPublisher;

    public PayUCallbackGatewayImpl (PayUCommonGateway common, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.common = common;
        this.objectMapper = objectMapper;
        this.callbackHandler = new DelegatePayUCallbackHandler();
        this.eventPublisher = eventPublisher;
    }

    @Override
    public AbstractPaymentCallbackResponse handle(PayUCallbackRequestPayload request) {
        return callbackHandler.handle(request);
    }

    @Override
    public PayUCallbackRequestPayload parse(Map<String, Object> payload) {
        return callbackHandler.parse(payload);
    }

    private class DelegatePayUCallbackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> {

        private final Map<String, IPaymentCallback<?,?>> delegator = new HashMap<>();

        public DelegatePayUCallbackHandler() {
            this.delegator.put(PayUConstants.GENERIC_CALLBACK_ACTION, new GenericPayUCallbackHandler());
            this.delegator.put(PayUConstants.REFUND_CALLBACK_ACTION, new RefundPayUCallBackHandler());
        }

        @Override
        public AbstractPaymentCallbackResponse handle(PayUCallbackRequestPayload request) {
            final Transaction transaction = TransactionContext.get();
            final String transactionId = transaction.getIdStr();
            try {
                final String errorCode = request.getError();
                final String errorMessage = request.getErrorMessage();
                final IPaymentCallback callbackService = delegator.get(request.getAction());
                if (validate(request)) {
                    return callbackService.handle(request);
                } else {
                    log.error(PAYU_CHARGING_CALLBACK_FAILURE, "Invalid checksum found with transactionStatus: {}, Wynk transactionId: {}, PayU transactionId: {}, Reason: error code: {}, error message: {} for uid: {}", request.getStatus(), transactionId, request.getExternalTransactionId(), errorCode, errorMessage, transaction.getUid());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found with transaction id:" + transactionId);
                }
            } catch (Exception e) {
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
            }
        }

        @Override
        public PayUCallbackRequestPayload parse(Map<String, Object> payload) {
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
            public AbstractPaymentCallbackResponse handle(PayUCallbackRequestPayload request) {
                final Transaction transaction = TransactionContext.get();
                common.syncTransactionWithSourceResponse(transaction, PayUVerificationResponse.<PayUChargingTransactionDetails>builder().status(1).transactionDetails(Collections.singletonMap(transaction.getIdStr(), AbstractPayUTransactionDetails.from(request))).build());
                if (!EnumSet.of(PaymentEvent.RENEW, PaymentEvent.REFUND).contains(transaction.getType())) {
                    Optional<IPurchaseDetails> optionalDetails = TransactionContext.getPurchaseDetails();
                    if (optionalDetails.isPresent()) {
                        final String redirectionUrl;
                        IChargingDetails chargingDetails = (IChargingDetails) optionalDetails.get();
                        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                            log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
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
        }

        private class RefundPayUCallBackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, PayUAutoRefundCallbackRequestPayload> {

            @Override
            public AbstractPaymentCallbackResponse handle(PayUAutoRefundCallbackRequestPayload request) {
                final Transaction transaction = TransactionContext.get();
                common.syncTransactionWithSourceResponse(transaction, PayUVerificationResponse.<PayUChargingTransactionDetails>builder().status(1).transactionDetails(Collections.singletonMap(transaction.getIdStr(), AbstractPayUTransactionDetails.from(request))).build());
                if (transaction.getStatus() == TransactionStatus.SUCCESS)
                    transaction.setStatus(TransactionStatus.AUTO_REFUND.getValue());
                return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
            }


        }
    }

    private boolean validate(PayUCallbackRequestPayload callbackRequest) {
        final Transaction transaction = TransactionContext.get();
        final String transactionId = transaction.getIdStr();
        final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        return validateCallbackChecksum(payUMerchantKey, payUMerchantSecret, transactionId, callbackRequest.getStatus(), callbackRequest.getUdf(), callbackRequest.getEmail(), callbackRequest.getFirstName(), ((transaction.getType()== PaymentEvent.POINT_PURCHASE) ? transaction.getItemId(): String.valueOf(transaction.getPlanId())), transaction.getAmount(), callbackRequest.getResponseHash());
    }

    @SneakyThrows
    private boolean validateCallbackChecksum(String payUMerchantKey, String payUSalt, String transactionId, String transactionStatus, String udf1, String email, String firstName, String planTitle, double amount, String payUResponseHash) {
        final DecimalFormat df = new DecimalFormat("#0.00");
        final String generatedString = payUSalt + PIPE_SEPARATOR + transactionStatus + "||||||" + udf1 + PIPE_SEPARATOR + URLDecoder.decode(email, String.valueOf(StandardCharsets.UTF_8)) + PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + df.format(amount) + PIPE_SEPARATOR + transactionId + PIPE_SEPARATOR + payUMerchantKey;
        final String generatedHash = EncryptionUtils.generateSHA512Hash(generatedString);
        assert generatedHash != null;
        return generatedHash.equals(payUResponseHash);
    }
}

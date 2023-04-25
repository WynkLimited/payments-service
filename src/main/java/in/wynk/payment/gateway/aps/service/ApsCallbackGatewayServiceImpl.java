package in.wynk.payment.gateway.aps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.request.callback.ApsAutoRefundCallbackRequestPayload;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;
import in.wynk.payment.dto.aps.request.status.refund.RefundStatusRequest;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.callback.DefaultPaymentCallbackResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.utils.aps.SignatureUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY006;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_CHARGING_CALLBACK_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsCallbackGatewayServiceImpl implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> {

    private final String REFUND_CALLBACK_TYPE = "REFUND_STATUS";
    private final String PAYMENT_STATUS_CALLBACK_TYPE = "PAYMENT_STATUS";
    private final String salt;
    private final String secret;
    private final ApsCommonGatewayService common;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, IPaymentCallback<? extends AbstractPaymentCallbackResponse, ? extends ApsCallBackRequestPayload>> delegator = new HashMap<>();

    public ApsCallbackGatewayServiceImpl(String salt, String secret, ApsCommonGatewayService common, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.salt = salt;
        this.secret = secret;
        this.common = common;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.delegator.put(PAYMENT_STATUS_CALLBACK_TYPE, new GenericApsCallbackHandler());
        this.delegator.put(REFUND_CALLBACK_TYPE, new RefundApsCallBackHandler());
    }

    @Override
    public AbstractPaymentCallbackResponse handle(ApsCallBackRequestPayload request) {
        final String callbackType = Optional.ofNullable(request.getType().toString()).orElse(PAYMENT_STATUS_CALLBACK_TYPE);
        final IPaymentCallback callbackService = delegator.get(callbackType);
        if (isValid(request)) {
            return callbackService.handle(request);
        } else {
            log.error(APS_CHARGING_CALLBACK_FAILURE, "Invalid checksum found with transactionStatus: {}, APS transactionId: {}", request.getStatus(), request.getOrderId());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found with transaction id:" + request.getOrderId());
        }
    }

    @Override
    public ApsCallBackRequestPayload parse(Map<String, Object> payload) {
        try {
            final String type = ((String) payload.getOrDefault("type", PAYMENT_STATUS_CALLBACK_TYPE));
            return delegator.get(type).parse(payload);
        } catch (Exception e) {
            throw new WynkRuntimeException(PAY006, e);
        }
    }

    @SneakyThrows
    public boolean isValid(ApsCallBackRequestPayload payload) {
        return SignatureUtil.verifySignature(payload.getChecksum(), payload, secret, salt);
    }

    private class GenericApsCallbackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> {

        @Override
        public AbstractPaymentCallbackResponse handle(ApsCallBackRequestPayload request) {
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

        @Override
        public ApsCallBackRequestPayload parse(Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return objectMapper.readValue(json, ApsCallBackRequestPayload.class);
            } catch (Exception e) {
                throw new WynkRuntimeException(PAY006, e);
            }
        }
    }

    private class RefundApsCallBackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsAutoRefundCallbackRequestPayload> {

        @Override
        public AbstractPaymentCallbackResponse handle (ApsAutoRefundCallbackRequestPayload request) {
            final Transaction transaction = TransactionContext.get();
            if (request.isAutoRefund()) {
                updateTransaction(request, transaction);
                if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    transaction.setStatus(TransactionStatus.AUTO_REFUND.getValue());
                }
            } else {
                common.syncRefundTransactionFromSource(transaction, request.getRefundId());
                if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    transaction.setStatus(TransactionStatus.REFUNDED.getValue());
                }
            }

            return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
        }

        private void updateTransaction (ApsAutoRefundCallbackRequestPayload request, Transaction transaction) {
            TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
            final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
            try {
                final RefundStatusRequest refundStatusRequest = RefundStatusRequest.builder().refundId(request.getRefundId()).build();
                mBuilder.request(refundStatusRequest);
                mBuilder.externalTransactionId(request.getRefundId());
                if (!StringUtils.isEmpty(request.getStatus()) && request.getStatus().toString().equalsIgnoreCase("SUCCESS")) {
                    finalTransactionStatus = TransactionStatus.SUCCESS;
                } else if (!StringUtils.isEmpty(request.getStatus()) && request.getStatus().toString().equalsIgnoreCase("FAILED")) {
                    finalTransactionStatus = TransactionStatus.FAILURE;
                }
            } finally {
                transaction.setStatus(finalTransactionStatus.name());
                eventPublisher.publishEvent(mBuilder.build());
            }
        }

        @Override
        public ApsAutoRefundCallbackRequestPayload parse (Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return objectMapper.readValue(json, ApsAutoRefundCallbackRequestPayload.class);
            } catch (Exception e) {
                throw new WynkRuntimeException(PAY006, e);
            }
        }
    }
}

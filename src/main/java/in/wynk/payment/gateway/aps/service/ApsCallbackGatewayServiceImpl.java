package in.wynk.payment.gateway.aps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.aps.common.WebhookConfigType;
import in.wynk.payment.dto.aps.request.callback.*;
import in.wynk.payment.dto.aps.request.status.refund.RefundStatusRequest;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.callback.DefaultPaymentCallbackResponse;
import in.wynk.payment.event.PaymentCallbackKafkaMessage;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.stream.service.IDataPlatformKafkaService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.payment.utils.aps.SignatureUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

import java.util.*;

import static in.wynk.payment.core.constant.PaymentConstants.PIPE_SEPARATOR;
import static in.wynk.payment.core.constant.PaymentErrorType.APS011;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_CALLBACK_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_CHARGING_STATUS_VERIFICATION;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsCallbackGatewayServiceImpl implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> {

    private final String salt;
    private final String secret;
    private final ApsCommonGatewayService common;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RecurringTransactionUtils recurringTransactionUtils;
    private final IDataPlatformKafkaService dataPlatformKafkaService;
    private final Map<String, IPaymentCallback<? extends AbstractPaymentCallbackResponse, ? extends ApsCallBackRequestPayload>> delegator = new HashMap<>();

    public ApsCallbackGatewayServiceImpl(String salt, String secret, ApsCommonGatewayService common, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher, RecurringTransactionUtils recurringTransactionUtils, IDataPlatformKafkaService dataPlatformKafkaService) {
        this.salt = salt;
        this.secret = secret;
        this.common = common;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.recurringTransactionUtils = recurringTransactionUtils;
        this.dataPlatformKafkaService = dataPlatformKafkaService;
        this.delegator.put(WebhookConfigType.PAYMENT_STATUS.name(), new GenericApsCallbackHandler());
        this.delegator.put(WebhookConfigType.REFUND_STATUS.name(), new RefundApsCallBackHandler());
        this.delegator.put(WebhookConfigType.MANDATE_STATUS.name(), new MandateStatusApsCallBackHandler());
    }

    @Override
    public AbstractPaymentCallbackResponse handle(ApsCallBackRequestPayload request) {
        Optional<WebhookConfigType> webhookConfigType = Optional.ofNullable(request.getType());
        final String callbackType;
        if (webhookConfigType.isPresent()) {
            callbackType = request.getType().toString();
        } else {
            callbackType = WebhookConfigType.PAYMENT_STATUS.name();
        }
        final IPaymentCallback callbackService = delegator.get(callbackType);
        if (isValid(request)) {
            return callbackService.handle(request);
        } else {
            log.error(APS_CALLBACK_FAILURE, "Invalid checksum found with transactionStatus: {}, APS transactionId: {}", request.getStatus(), request.getOrderId());
            throw new PaymentRuntimeException(PaymentErrorType.APS009, "Invalid checksum found with transaction id:" + request.getOrderId());
        }
    }

    @Override
    public ApsCallBackRequestPayload parse(Map<String, Object> payload) {
        try {
            final String type = ((String) payload.getOrDefault("type", WebhookConfigType.PAYMENT_STATUS.name()));
            return delegator.get(type).parse(payload);
        } catch (Exception e) {
            throw new WynkRuntimeException(APS011, e);
        }
    }

    @SneakyThrows
    public boolean isValid(ApsCallBackRequestPayload payload) {
        try {
            if (payload instanceof ApsOrderStatusCallBackPayload) {
                return validate((ApsOrderStatusCallBackPayload) payload);
            } else {
                return SignatureUtil.verifySignature(Objects.nonNull(payload.getChecksum()) ? payload.getChecksum() : payload.getSignature(),
                        Objects.isNull(payload.getSignature()) ? payload : objectMapper.convertValue(payload, ApsRedirectCallBackCheckSumPayload.class), secret, salt);
            }
        } catch (Exception ex) {
            log.error(APS_CALLBACK_FAILURE, "There is some issue in checksum for callbackStatus: {}, APS transactionId: {}", payload.getStatus(), payload.getOrderId(), ex);
            throw new PaymentRuntimeException(PaymentErrorType.APS009, "Exception occurred due to checksum from aps with transaction id:" + payload.getOrderId());
        }
    }

    @SneakyThrows
    private boolean validate (ApsOrderStatusCallBackPayload payload) {
        //This is temporary change. Need to ask APS for adding hash and lob for redirection flow of cards
        if(Objects.isNull(payload.getHash())) {
            return true;
        }
        final String generatedString = payload.getOrderId() + PIPE_SEPARATOR + payload.getOrderInfo().getOrderStatus() + PIPE_SEPARATOR + payload.getOrderInfo().getRequester() + PIPE_SEPARATOR +
                payload.getPaymentDetails()[0].getPgId() + PIPE_SEPARATOR + payload.getPaymentDetails()[0].getPaymentStatus() + PIPE_SEPARATOR + payload.getFulfilmentInfo()[0].getFulfilmentId() +
                PIPE_SEPARATOR + payload.getFulfilmentInfo()[0].getStatus() + PIPE_SEPARATOR + salt;
        final String generatedHash = EncryptionUtils.generateSHA512Hash(generatedString);
        assert generatedHash != null;
        return generatedHash.equals(payload.getHash());
    }

    private class GenericApsCallbackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> {

        @Override
        public AbstractPaymentCallbackResponse handle(ApsCallBackRequestPayload request) {
            final Transaction transaction = TransactionContext.get();
            if (BeanConstant.AIRTEL_PAY_STACK_V2.equalsIgnoreCase(transaction.getPaymentChannel().getCode())) {
                common.syncOrderTransactionFromSource(transaction);
            } else {
                common.syncChargingTransactionFromSource(transaction, request.getRedirectionDestination() == null ? Optional.of(ApsChargeStatusResponse.from(request)): Optional.empty());
            }
            if (!EnumSet.of(PaymentEvent.RENEW, PaymentEvent.REFUND).contains(transaction.getType())) {
                Optional<IPurchaseDetails> optionalDetails = TransactionContext.getPurchaseDetails();
                if (optionalDetails.isPresent()) {
                    final String redirectionUrl;
                    IChargingDetails chargingDetails = (IChargingDetails) optionalDetails.get();
                    if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                        log.warn(APS_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at aps end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                        redirectionUrl = chargingDetails.getPageUrlDetails().getPendingPageUrl();
                    } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                        log.warn(APS_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at aps end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                        redirectionUrl = chargingDetails.getPageUrlDetails().getUnknownPageUrl();
                    } else if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                        redirectionUrl = chargingDetails.getPageUrlDetails().getSuccessPageUrl();
                    } else {
                        redirectionUrl = chargingDetails.getPageUrlDetails().getFailurePageUrl();
                    }
                    return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).redirectUrl(redirectionUrl).build();
                }
            }
            dataPlatformKafkaService.publish(PaymentCallbackKafkaMessage.from(request, transaction));
            return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
        }

        @Override
        public ApsCallBackRequestPayload parse(Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return json.contains("PREPAID") ? objectMapper.readValue(json, ApsOrderStatusCallBackPayload.class) :objectMapper.readValue(json, ApsCallBackRequestPayload.class);
            } catch (Exception e) {
                throw new WynkRuntimeException(APS011, e);
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
            dataPlatformKafkaService.publish(PaymentCallbackKafkaMessage.from(request, transaction));
            return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
        }

        private void updateTransaction (ApsAutoRefundCallbackRequestPayload request, Transaction transaction) {
            TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
            final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());

            final RefundStatusRequest refundStatusRequest = RefundStatusRequest.builder().refundId(request.getRefundId()).build();
            mBuilder.request(refundStatusRequest);
            mBuilder.externalTransactionId(request.getRefundId());
            if (!StringUtils.isEmpty(request.getStatus()) && request.getStatus().toString().equalsIgnoreCase("SUCCESS")) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (!StringUtils.isEmpty(request.getStatus()) && request.getStatus().toString().equalsIgnoreCase("FAILED")) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
            transaction.setStatus(finalTransactionStatus.name());
            if (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(transaction.getType())) {
                common.syncChargingTransactionFromSource(transaction, Optional.empty());
            } else {
                eventPublisher.publishEvent(mBuilder.build());
            }
        }

        @Override
        public ApsAutoRefundCallbackRequestPayload parse (Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return objectMapper.readValue(json, ApsAutoRefundCallbackRequestPayload.class);
            } catch (Exception e) {
                throw new WynkRuntimeException(APS011, e);
            }
        }
    }

    private class MandateStatusApsCallBackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsMandateStatusCallbackRequestPayload> {
        @Override
        public AbstractPaymentCallbackResponse handle (ApsMandateStatusCallbackRequestPayload callbackRequest) {
            final Transaction transaction = TransactionContext.get();
            String description = "Mandate is already " + callbackRequest.getMandateStatus().name().toLowerCase(Locale.ROOT);
            recurringTransactionUtils.cancelRenewalBasedOnRealtimeMandate(description, transaction);
            transaction.setStatus(TransactionStatus.CANCELLED.getValue());
            dataPlatformKafkaService.publish(PaymentCallbackKafkaMessage.from(callbackRequest, transaction));
            return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
        }

        @Override
        public ApsMandateStatusCallbackRequestPayload parse (Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return objectMapper.readValue(json, ApsMandateStatusCallbackRequestPayload.class);
            } catch (Exception e) {
                throw new WynkRuntimeException(APS011, e);
            }
        }
    }
}

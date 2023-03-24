package in.wynk.payment.gateway.aps.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.callback.DefaultPaymentCallbackResponse;
import in.wynk.payment.dto.payu.ApsAutoRefundCallbackRequestPayload;
import in.wynk.payment.dto.payu.ApsCallBackRequestPayload;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.gateway.aps.common.ApsCommonGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY006;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(PaymentConstants.APS_CALLBACK)
public class ApsCallbackGateway implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> {

    private final ApsCommonGateway common;
    private final ObjectMapper objectMapper;
    private final IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> callbackHandler;

    public ApsCallbackGateway(ApsCommonGateway common, ObjectMapper objectMapper) {
        this.common = common;
        this.objectMapper = objectMapper;
        this.callbackHandler = new DelegateApsCallbackHandler();
    }

    @Override
    public AbstractPaymentCallbackResponse handleCallback (ApsCallBackRequestPayload callbackRequest) {
        return callbackHandler.handleCallback(callbackRequest);
    }

    private class DelegateApsCallbackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> {

        private final Map<Class<? extends IPaymentCallback<? extends AbstractPaymentCallbackResponse, ? extends ApsCallBackRequestPayload>>, IPaymentCallback> delegator = new HashMap<>();

        public DelegateApsCallbackHandler() {
            this.delegator.put(GenericApsCallbackHandler.class, new GenericApsCallbackHandler());
            this.delegator.put(RefundApsCallBackHandler.class, new RefundApsCallBackHandler());
        }

        @Override
        public AbstractPaymentCallbackResponse handleCallback(ApsCallBackRequestPayload callbackRequest) {
            final Transaction transaction = TransactionContext.get();
            try {
                final IPaymentCallback callbackService = delegator.get(
                        ApsAutoRefundCallbackRequestPayload.class.isAssignableFrom(callbackRequest.getClass()) ? RefundApsCallBackHandler.class : GenericApsCallbackHandler.class);
                    return callbackService.handleCallback(callbackRequest);
            } catch (Exception e) {
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
            }
        }

        @Override
        public ApsCallBackRequestPayload parseCallback(Map<String, Object> payload) {
            try {
                final String json = objectMapper.writeValueAsString(payload);
                return objectMapper.readValue(json, ApsCallBackRequestPayload.class);
            } catch (Exception e) {
                throw new WynkRuntimeException(PAY006, e);
            }
        }

        private class GenericApsCallbackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> {

            @Override
            public AbstractPaymentCallbackResponse handleCallback(ApsCallBackRequestPayload callbackRequest) {
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

        private class RefundApsCallBackHandler implements IPaymentCallback<AbstractPaymentCallbackResponse, ApsAutoRefundCallbackRequestPayload> {

            @Override
            public AbstractPaymentCallbackResponse handleCallback(ApsAutoRefundCallbackRequestPayload callbackRequest) {
                final Transaction transaction = TransactionContext.get();
                common.syncRefundTransactionFromSource(transaction, callbackRequest.getRequestId());
                // if an auto refund transaction is successful after recon from payu then transaction status should be marked as auto refunded
                if (transaction.getStatus() == TransactionStatus.SUCCESS)
                    transaction.setStatus(TransactionStatus.AUTO_REFUND.getValue());
                return DefaultPaymentCallbackResponse.builder().transactionStatus(transaction.getStatus()).build();
            }


        }
    }
}

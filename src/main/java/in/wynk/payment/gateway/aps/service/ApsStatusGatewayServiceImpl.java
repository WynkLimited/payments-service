package in.wynk.payment.gateway.aps.service;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.DefaultPaymentStatusResponse;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.gateway.IPaymentStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY888;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsStatusGatewayServiceImpl implements IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

    private final ApsCommonGatewayService common;

    private final Map<Class<? extends AbstractTransactionReconciliationStatusRequest>, IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>>
            statusDelegate = new HashMap<>();

    public ApsStatusGatewayServiceImpl(ApsCommonGatewayService common) {
        this.common = common;
        this.statusDelegate.put(ChargingTransactionReconciliationStatusRequest.class, new ChargingTransactionReconciliationStatusService());
        this.statusDelegate.put(RefundTransactionReconciliationStatusRequest.class, new RefundTransactionReconciliationStatusService());
        this.statusDelegate.put(RenewalChargingTransactionReconciliationStatusRequest.class, new RenewalChargingTransactionReconciliationStatusService());
    }

    @Override
    public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> reconStatusService =
                statusDelegate.get(request.getClass());
        if (Objects.isNull(reconStatusService)) {
            throw new WynkRuntimeException(PAY888, "recon service mapping not fund for uid: " + transaction.getUid());
        }
        return reconStatusService.reconcile(request);
    }

    private class ChargingTransactionReconciliationStatusService implements IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            if (ApsConstant.AIRTEL_PAY_STACK_V2.equalsIgnoreCase(transaction.getPaymentChannel().getCode())) {
                common.syncOrderTransactionFromSource(transaction);
            } else {
                common.syncChargingTransactionFromSource(transaction, Optional.empty());
            }
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.warn(APS_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.APS005);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.warn(APS_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.APS006);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();
        }
    }

    private class RefundTransactionReconciliationStatusService implements IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            RefundTransactionReconciliationStatusRequest refundRequest = (RefundTransactionReconciliationStatusRequest) request;
            common.syncRefundTransactionFromSource(transaction, refundRequest.getExtTxnId());
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.warn(APS_REFUND_STATUS_VERIFICATION, "Refund Transaction is still pending at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.APS005);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.warn(APS_REFUND_STATUS_VERIFICATION, "Unknown Refund Transaction status at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.APS006);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();
        }
    }

    private class RenewalChargingTransactionReconciliationStatusService implements IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            common.syncChargingTransactionFromSource(transaction, Optional.empty());
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.warn(APS_RENEWAL_STATUS_VERIFICATION, "Renewal transaction is still pending at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.APS005);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.warn(APS_RENEWAL_STATUS_VERIFICATION, "Unknown renewal transaction status at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.APS006);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();
        }
    }
}

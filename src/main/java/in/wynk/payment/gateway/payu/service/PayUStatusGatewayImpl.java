package in.wynk.payment.gateway.payu.service;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.DefaultPaymentStatusResponse;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.gateway.IPaymentStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY003;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;

@Slf4j
public class PayUStatusGatewayImpl implements IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

    private final PayUCommonGateway common;
    private final Map<Class<? extends AbstractTransactionReconciliationStatusRequest>, IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>>
            statusDelegate = new HashMap<>();

    public PayUStatusGatewayImpl(PayUCommonGateway common) {
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
            throw new WynkRuntimeException(PAY003, "Unknown transaction status request to process for uid: " + transaction.getUid() +" and transaction id: "+transaction.getIdStr());
        }
        return reconStatusService.reconcile(request);
    }

    private class ChargingTransactionReconciliationStatusService implements IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
                if (purchaseDetails.getPaymentDetails().isAutoRenew() && UpiPaymentDetails.class.isAssignableFrom(purchaseDetails.getPaymentDetails().getClass()) && TransactionStatus.INPROGRESS.equals(transaction.getStatus())) {
                    final UpiPaymentDetails upiPaymentDetails = (UpiPaymentDetails) purchaseDetails.getPaymentDetails();
                    if ( upiPaymentDetails.isIntent() && transaction.getInitTime().getTimeInMillis() + TimeUnit.MINUTES.toMillis(15) >= System.currentTimeMillis()) {
                        return reconcileInternal(transaction);
                    }
                }
            common.syncChargingTransactionFromSource(transaction, Optional.empty());
            return reconcileInternal(transaction);
        }

        private AbstractPaymentStatusResponse reconcileInternal(Transaction transaction) {
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY004);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY003);
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
                log.warn(PAYU_REFUND_STATUS_VERIFICATION, "Refund Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY004);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.warn(PAYU_REFUND_STATUS_VERIFICATION, "Unknown Refund Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY003);
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
                log.warn(PAYU_RENEWAL_CHARGING_STATUS_VERIFICATION, "Renewal transaction is still pending at PAYU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY004);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.warn(PAYU_RENEWAL_CHARGING_STATUS_VERIFICATION, "Unknown renewal transaction status at PAYU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY003);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();
        }
    }
}

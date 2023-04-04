package in.wynk.payment.gateway.aps.service;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.DefaultPaymentStatusResponse;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.ChargingTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.RefundTransactionReconciliationStatusRequest;
import in.wynk.payment.service.IPaymentStatusService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY889;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_REFUND_STATUS_VERIFICATION;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsStatusGatewayService implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

    private final ApsCommonGatewayService common;

    private final Map<Class<? extends AbstractTransactionReconciliationStatusRequest>, IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>>
            statusDelegate = new HashMap<>();

    public ApsStatusGatewayService (ApsCommonGatewayService common) {
        this.common = common;
        this.statusDelegate.put(ChargingTransactionReconciliationStatusRequest.class, new ChargingTransactionReconciliationStatusService());
        this.statusDelegate.put(RefundTransactionReconciliationStatusRequest.class, new RefundTransactionReconciliationStatusService());
    }

    @Override
    public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> reconStatusService =
                statusDelegate.get(request.getClass());
        if (Objects.isNull(reconStatusService)) {
            throw new WynkRuntimeException(PAY889, "Unknown transaction status request to process for uid: " + transaction.getUid());
        }
        return reconStatusService.status(request);
    }

    private class ChargingTransactionReconciliationStatusService implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            common.syncChargingTransactionFromSource(transaction);
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.error(APS_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY004);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.error(APS_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY003);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();
        }
    }

    private class RefundTransactionReconciliationStatusService implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            RefundTransactionReconciliationStatusRequest refundRequest = (RefundTransactionReconciliationStatusRequest) request;
            common.syncRefundTransactionFromSource(transaction, refundRequest.getExtTxnId());
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.error(APS_REFUND_STATUS_VERIFICATION, "Refund Transaction is still pending at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY004);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.error(APS_REFUND_STATUS_VERIFICATION, "Unknown Refund Transaction status at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY003);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();
        }
    }


}

package in.wynk.payment.presentation;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentErrorDetails;
import in.wynk.payment.dto.TransactionDetailsDtoV3;
import in.wynk.payment.dto.TransactionSnapShot;
import in.wynk.payment.dto.request.ChargingTransactionStatusRequest;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.service.impl.PaymentErrorServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class PaymentChargingStatusPresentationV2 implements IPresentation<WynkResponse<TransactionDetailsDtoV3>, TransactionSnapShot> {
    private final PaymentCommonPresentation commonPresentation;
    private final PaymentCachingService cachingService;
    private final PaymentErrorServiceImpl paymentErrorService;

    @SneakyThrows
    @Override
    public WynkResponse<TransactionDetailsDtoV3> transform(TransactionSnapShot payload) {
        int planId = payload.getTransactionDetails().getTransaction().getType() == in.wynk.common.enums.PaymentEvent.TRIAL_SUBSCRIPTION ? cachingService.getPlan(payload.getTransactionDetails().getTransaction().getPlanId()).getLinkedFreePlanId() : payload.getTransactionDetails().getTransaction().getPlanId();
        final Transaction transaction = payload.getTransactionDetails().getTransaction();
        TransactionDetailsDtoV3.TransactionDetailsDtoV3Builder transactionDetailsBuilder = TransactionDetailsDtoV3.builder();
        transactionDetailsBuilder.tid(transaction.getIdStr()).planId(transaction.getPlanId()).amountPaid(transaction.getAmount()).discount(transaction.getDiscount()).creationDate(transaction.getInitTime()).status(transaction.getStatus()).validity(cachingService.validTillDate(planId,payload.getTransactionDetails().getTransaction().getMsisdn()));
        transactionDetailsBuilder.purchaseDetails(commonPresentation.getPurchaseDetails(payload.getTransactionDetails().getPurchaseDetails()));
        transactionDetailsBuilder.packDetails(commonPresentation.getPackDetails(payload.getTransactionDetails().getTransaction(), ChargingTransactionStatusRequest.builder().transactionId(payload.getTransactionDetails().getTransaction().getIdStr()).planId(planId).build()));
        final TransactionStatus txnStatus = transaction.getStatus();
        if (!EnumSet.of(TransactionStatus.SUCCESS).contains(txnStatus)) {
            transactionDetailsBuilder.errorDetails(getPaymentErrorDetails(transaction));
        }
        return WynkResponse.<TransactionDetailsDtoV3>builder().body(transactionDetailsBuilder.build()).build();
    }

    private PaymentErrorDetails getPaymentErrorDetails (Transaction transaction) {
        try{
            PaymentError paymentError = paymentErrorService.getPaymentError(transaction.getIdStr());
            return PaymentErrorDetails.builder().code(paymentError.getCode()).title(paymentError.getDescription()).description(paymentError.getDescription()).build();
        } catch(Exception e){
            return PaymentErrorDetails.builder().code(BaseConstants.UNKNOWN).title(BaseConstants.UNKNOWN).description(BaseConstants.UNKNOWN).build();
        }
    }
}

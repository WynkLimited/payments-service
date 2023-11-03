package in.wynk.payment.presentation;

import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.WynkResponse;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionDetailsDto;
import in.wynk.payment.dto.TransactionSnapShot;
import in.wynk.payment.dto.request.ChargingTransactionStatusRequest;
import in.wynk.payment.service.PaymentCachingService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;

@Service
@RequiredArgsConstructor
public class PaymentChargingStatusPresentation implements IPresentation<WynkResponse<TransactionDetailsDto>, TransactionSnapShot> {
    private final PaymentCommonPresentation commonPresentation;
    private final PaymentCachingService cachingService;

    @SneakyThrows
    @Override
    public WynkResponse<TransactionDetailsDto> transform(TransactionSnapShot payload) {
        int planId = payload.getTransactionDetails().getTransaction().getType() == in.wynk.common.enums.PaymentEvent.TRIAL_SUBSCRIPTION ? cachingService.getPlan(payload.getTransactionDetails().getTransaction().getPlanId()).getLinkedFreePlanId() : payload.getTransactionDetails().getTransaction().getPlanId();
        final Transaction transaction = payload.getTransactionDetails().getTransaction();
        TransactionDetailsDto.TransactionDetailsDtoBuilder transactionDetailsBuilder = TransactionDetailsDto.builder();
        transactionDetailsBuilder.tid(transaction.getIdStr()).planId(transaction.getPlanId()).amountPaid(transaction.getAmount()).discount(transaction.getDiscount()).creationDate(transaction.getInitTime()).status(transaction.getStatus()).validity(cachingService.validTillDate(planId,payload.getTransactionDetails().getTransaction().getMsisdn()));
        transactionDetailsBuilder.purchaseDetails(commonPresentation.getPurchaseDetails(payload.getTransactionDetails().getPurchaseDetails()));
        transactionDetailsBuilder.packDetails(commonPresentation.getPackDetails(payload.getTransactionDetails().getTransaction(), ChargingTransactionStatusRequest.builder().transactionId(payload.getTransactionDetails().getTransaction().getIdStr()).planId(planId).build()));
        return WynkResponse.<TransactionDetailsDto>builder().body(transactionDetailsBuilder.build()).build();
    }
}

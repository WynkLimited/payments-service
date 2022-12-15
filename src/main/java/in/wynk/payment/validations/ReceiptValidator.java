package in.wynk.payment.validations;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.validations.BaseHandler;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.ItunesReceiptDetails;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.dto.amazonIap.AmazonLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.itune.ItunesLatestReceiptResponse;
import in.wynk.payment.dto.itune.LatestReceiptInfo;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
public class ReceiptValidator extends BaseHandler<IReceiptValidatorRequest<LatestReceiptResponse>> {

    private final Map<String, BaseHandler> delegate = new HashMap<>();

    public ReceiptValidator() {
        delegate.put(ITUNES, new ItunesReceiptValidator());
        delegate.put(AMAZON_IAP, new AmazonReceiptValidator());
        delegate.put(GOOGLE_IAP, new GooglePlayReceiptValidator());
    }

    @Override
    public void handle(IReceiptValidatorRequest<LatestReceiptResponse> request) {
        final PaymentCode code = request.getPaymentCode();
        delegate.get(code.getId()).handle(request);
    }

    private static class ItunesReceiptValidator extends BaseHandler<IReceiptValidatorRequest<ItunesLatestReceiptResponse>> {

        @Override
        public void handle(IReceiptValidatorRequest<ItunesLatestReceiptResponse> request) {
            final ItunesLatestReceiptResponse receiptResponse = request.getLatestReceiptInfo();
            final LatestReceiptInfo latestReceiptInfo = receiptResponse.getLatestReceiptInfo().get(0);
            final String receiptTransactionId = receiptResponse.getItunesReceiptType().getTransactionId(latestReceiptInfo);
            final long originalTransactionId = receiptResponse.getItunesReceiptType().getOriginalTransactionId(latestReceiptInfo);
            final Optional<ReceiptDetails> receiptDetailsOption = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findById(String.valueOf(originalTransactionId));
            if (receiptDetailsOption.isPresent()) {
                final ItunesReceiptDetails receiptDetails = (ItunesReceiptDetails) receiptDetailsOption.get();
                String txnId = receiptDetails.getPaymentTransactionId();
                final Transaction transaction = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).findById(txnId).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010, txnId));
                if (receiptTransactionId.equalsIgnoreCase(receiptDetails.getReceiptTransactionId()) && TransactionStatus.SUCCESS == (transaction.getStatus())) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY701);
                }
            }
            super.handle(request);
        }

    }

    private static class AmazonReceiptValidator extends BaseHandler<IReceiptValidatorRequest<AmazonLatestReceiptResponse>> {

        @Override
        public void handle(IReceiptValidatorRequest<AmazonLatestReceiptResponse> request) {
            LatestReceiptResponse receiptResponse = request.getLatestReceiptInfo();
            final String receiptId = receiptResponse.getExtTxnId();
            if (RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ReceiptDetailsDao.class).existsById(receiptId)) throw new WynkRuntimeException(PaymentErrorType.PAY701);
        }
    }

    private static class GooglePlayReceiptValidator extends BaseHandler<IReceiptValidatorRequest<GooglePlayLatestReceiptResponse>> {
        @Override
        public void handle (IReceiptValidatorRequest<GooglePlayLatestReceiptResponse> response) {
            GooglePlayLatestReceiptResponse latestReceiptInfo = response.getLatestReceiptInfo();
            Optional<ReceiptDetails> receiptDetailsOptional = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).
                    orElse(PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findById(latestReceiptInfo.getPurchaseToken());
            if (receiptDetailsOptional.isPresent()) {
                ReceiptDetails receiptDetails = receiptDetailsOptional.get();
                String txnId = receiptDetails.getPaymentTransactionId();
                final Transaction transaction = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).findById(txnId).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010, txnId));
                if (Objects.equals(receiptDetailsOptional.get().getNotificationType(), latestReceiptInfo.getNotificationType()) &&
                        !Objects.equals(latestReceiptInfo.getGooglePlayResponse().getLinkedPurchaseToken(), latestReceiptInfo.getPurchaseToken()) &&
                        TransactionStatus.SUCCESS == (transaction.getStatus())) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY701);
                }
            }
        }
    }
}

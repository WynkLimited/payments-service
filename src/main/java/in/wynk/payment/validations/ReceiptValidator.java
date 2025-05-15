package in.wynk.payment.validations;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.validations.BaseHandler;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.dto.amazonIap.AmazonLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayProductReceiptResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlaySubscriptionReceiptResponse;
import in.wynk.payment.dto.itune.ItunesLatestReceiptResponse;
import in.wynk.payment.dto.itune.LatestReceiptInfo;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
public class ReceiptValidator extends BaseHandler<IReceiptValidatorRequest<LatestReceiptResponse>> {

    private final Map<String, BaseHandler> delegate = new HashMap<>();

    public ReceiptValidator () {
        delegate.put(ITUNES, new ItunesReceiptValidator());
        delegate.put(AMAZON_IAP, new AmazonReceiptValidator());
        delegate.put(GOOGLE_IAP, new GooglePlayReceiptValidator());
    }

    @Override
    public void handle (IReceiptValidatorRequest<LatestReceiptResponse> request) {
        final PaymentGateway code = request.getPaymentCode();
        delegate.get(code.getId()).handle(request);
    }

    private static class ItunesReceiptValidator extends BaseHandler<IReceiptValidatorRequest<ItunesLatestReceiptResponse>> {

        @Override
        public void handle (IReceiptValidatorRequest<ItunesLatestReceiptResponse> request) {
            final ItunesLatestReceiptResponse receiptResponse = request.getLatestReceiptInfo();
            final LatestReceiptInfo latestReceiptInfo = receiptResponse.getLatestReceiptInfo().get(0);
            final String receiptTransactionId = receiptResponse.getItunesReceiptType().getTransactionId(latestReceiptInfo);
            final long originalTransactionId = receiptResponse.getItunesReceiptType().getOriginalTransactionId(latestReceiptInfo);
            final Optional<ReceiptDetails> receiptDetailsOptional =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                            .findById(String.valueOf(originalTransactionId));
            if (receiptDetailsOptional.isPresent() && verifyIfPreviousTransactionSuccess(receiptDetailsOptional.get()) &&
                    receiptTransactionId.equalsIgnoreCase(receiptDetailsOptional.get().getReceiptTransactionId())) {
                SessionContextHolder.<SessionDTO>getBody().put(TXN_ID, receiptDetailsOptional.get().getPaymentTransactionId());
                throw new WynkRuntimeException(PaymentErrorType.PAY701);
            }
            receiptDetailsOptional.ifPresent(details -> {
                SessionContextHolder.<SessionDTO>getBody().put(OLD_TXN_ID, details.getPaymentTransactionId());
            });
            super.handle(request);
        }
    }

    private static class AmazonReceiptValidator extends BaseHandler<IReceiptValidatorRequest<AmazonLatestReceiptResponse>> {

        @Override
        public void handle (IReceiptValidatorRequest<AmazonLatestReceiptResponse> request) {
            LatestReceiptResponse receiptResponse = request.getLatestReceiptInfo();
            final String receiptId = receiptResponse.getExtTxnId();
            Optional<ReceiptDetails> receiptDetailsOptional =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findById(receiptId);
            if (receiptDetailsOptional.isPresent() && verifyIfPreviousTransactionSuccess(receiptDetailsOptional.get())) {
                SessionContextHolder.<SessionDTO>getBody().put(TXN_ID, receiptDetailsOptional.get().getPaymentTransactionId());
                throw new WynkRuntimeException(PaymentErrorType.PAY701);
            }
        }
    }

    private static class GooglePlayReceiptValidator extends BaseHandler<IReceiptValidatorRequest<GooglePlayLatestReceiptResponse>> {
        @Override
        public void handle (IReceiptValidatorRequest<GooglePlayLatestReceiptResponse> response) {
            GooglePlayLatestReceiptResponse latestReceiptInfo = response.getLatestReceiptInfo();
            if (latestReceiptInfo.getGooglePlayResponse() instanceof GooglePlayProductReceiptResponse) {
                GooglePlayProductReceiptResponse googlePlayResponse = (GooglePlayProductReceiptResponse) latestReceiptInfo.getGooglePlayResponse();
                if (googlePlayResponse.getConsumptionState() == 1) {
                    AnalyticService.update("AcknowledgementState", googlePlayResponse.getAcknowledgementState());
                    AnalyticService.update("ConsumptionState", googlePlayResponse.getConsumptionState());
                    throw new WynkRuntimeException(PaymentErrorType.PAY994);
                }
            }
            Optional<ReceiptDetails> receiptDetailsOptional =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                            .findById(latestReceiptInfo.getPurchaseToken());
            if (receiptDetailsOptional.isPresent()) {
                if (latestReceiptInfo.getPlanId() != 0) {
                    GooglePlaySubscriptionReceiptResponse subscriptionReceiptResponse = (GooglePlaySubscriptionReceiptResponse) latestReceiptInfo.getGooglePlayResponse();
                    if (verifyIfPreviousTransactionSuccess(receiptDetailsOptional.get()) &&
                            Objects.equals(receiptDetailsOptional.get().getNotificationType(), latestReceiptInfo.getNotificationType()) &&
                            !Objects.equals(subscriptionReceiptResponse.getLinkedPurchaseToken(), latestReceiptInfo.getPurchaseToken())) {
                        SessionContextHolder.<SessionDTO>getBody().put(TXN_ID, receiptDetailsOptional.get().getPaymentTransactionId());
                        throw new WynkRuntimeException(PaymentErrorType.PAY701);
                    }
                } else if (verifyIfPreviousTransactionSuccess(receiptDetailsOptional.get()) &&
                        Objects.equals(receiptDetailsOptional.get().getNotificationType(), latestReceiptInfo.getNotificationType())) {
                    SessionContextHolder.<SessionDTO>getBody().put(TXN_ID, receiptDetailsOptional.get().getPaymentTransactionId());
                    throw new WynkRuntimeException(PaymentErrorType.PAY701);
                }
            }
        }
    }

    public static boolean verifyIfPreviousTransactionSuccess (ReceiptDetails receiptDetails) {
        String txnId = receiptDetails.getPaymentTransactionId();
        final Transaction transaction = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).findById(txnId)
                .orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010, txnId));
        return TransactionStatus.SUCCESS == transaction.getStatus();
    }
}

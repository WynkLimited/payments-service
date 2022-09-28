package in.wynk.payment.validations;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.validations.BaseHandler;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.GooglePlayReceiptDetails;
import in.wynk.payment.core.dao.entity.ItunesReceiptDetails;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.dto.amazonIap.AmazonLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayReceiptResponse;
import in.wynk.payment.dto.itune.ItunesLatestReceiptResponse;
import in.wynk.payment.dto.itune.LatestReceiptInfo;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class ReceiptValidator extends BaseHandler<IReceiptValidatorRequest<LatestReceiptResponse>> {

    private final Map<String, BaseHandler> delegate = new HashMap<>();

    public ReceiptValidator() {
        delegate.put(PaymentConstants.ITUNES, new ItunesReceiptValidator());
        delegate.put(PaymentConstants.AMAZON_IAP, new AmazonReceiptValidator());
        delegate.put(PaymentConstants.GOOGLE_IAP, new GooglePlayReceiptValidator());
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
            final Optional<ReceiptDetails> receiptDetailsOption = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findById(String.valueOf(originalTransactionId));
            if(receiptDetailsOption.isPresent()) {
                final ItunesReceiptDetails receiptDetails = (ItunesReceiptDetails) receiptDetailsOption.get();
                if (receiptTransactionId.equalsIgnoreCase(receiptDetails.getReceiptTransactionId())) {
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
            if (RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).existsById(receiptId)) throw new WynkRuntimeException(PaymentErrorType.PAY701);
        }
    }

    /*
     * if latest receipt is null means wrong request
     * if latest response available from Google then check if data is present in db.
     * if data not present in db means request is for Purchase flow---> allow request
     * if data present in db for then request can be for renewal or other notifications
     * fraud check--> if expiration db in and latest expiration from google api is same means request is already processed
     * else if latest expiration is less than expiration in db, check for notification type,
     * if expiration notification--> update db with new notification type and throw error as nothing else should be done
     * if expiration is for future and notification is for Subscription cancelled, update db and do nothing
     * else expiration is in the past,subscription should be cancelled immediately--> should be done in the flow
     */
    private static class GooglePlayReceiptValidator extends BaseHandler<IReceiptValidatorRequest<GooglePlayLatestReceiptResponse>> {
        @Override
        public void handle (IReceiptValidatorRequest<GooglePlayLatestReceiptResponse> response) {
               if(RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).
                        orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).existsById(response.getLatestReceiptInfo().getPurchaseToken()))
                    throw new WynkRuntimeException(PaymentErrorType.PAY701);
            }
    }
}

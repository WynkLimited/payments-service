package in.wynk.payment.validations;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.validations.BaseHandler;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.ItunesReceiptDetails;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.dto.amazonIap.AmazonLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayReceiptResponse;
import in.wynk.payment.dto.itune.ItunesLatestReceiptResponse;
import in.wynk.payment.dto.itune.LatestReceiptInfo;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ReceiptValidator extends BaseHandler<IReceiptValidatorRequest<LatestReceiptResponse>> {

    private final Map<String, BaseHandler> delegate = new HashMap<>();

    public ReceiptValidator() {
        delegate.put(PaymentConstants.ITUNES, new ItunesReceiptValidator());
        delegate.put(PaymentConstants.AMAZON_IAP, new AmazonReceiptValidator());
        delegate.put(PaymentConstants.GPBS, new GooglePlayReceiptValidator());
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

    // If notification is Purchase notification--> expiration should not be in past
    // else if other notification--> process only if current expiration is in future else throw exception
    private static class GooglePlayReceiptValidator extends BaseHandler<IReceiptValidatorRequest<GooglePlayLatestReceiptResponse>> {

        @Override
        public void handle (IReceiptValidatorRequest<GooglePlayLatestReceiptResponse> response) {
            if (response.getLatestReceiptInfo().getGooglePlayResponse() != null) {
                String expiration = response.getLatestReceiptInfo().getGooglePlayResponse().getExpiryTimeMillis();
                LocalDateTime localDateTime = LocalDateTime.parse(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));

                long currentDate = localDateTime
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                if (expiration == null || (Long.parseLong(expiration, 10) - currentDate) < 0) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY701);
                }
            }
            throw new WynkRuntimeException(PaymentErrorType.PAY029);
        }
    }
}

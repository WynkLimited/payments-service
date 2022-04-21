package in.wynk.payment.validations;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.BaseHandler;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.ItunesReceiptDetails;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.dto.amazonIap.AmazonLatestReceiptResponse;
import in.wynk.payment.dto.itune.ItunesLatestReceiptResponse;
import in.wynk.payment.dto.itune.LatestReceiptInfo;
import in.wynk.payment.dto.response.LatestReceiptResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ReceiptValidator extends BaseHandler<IReceiptValidatorRequest<LatestReceiptResponse>> {

    private final Map<String, BaseHandler> delegate = new HashMap<>();

    public ReceiptValidator() {
        delegate.put(PaymentConstants.ITUNES, new ItunesReceiptValidator());
        delegate.put(PaymentConstants.AMAZON_IAP, new AmazonReceiptValidator());
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
            final long transactionId = receiptResponse.getItunesReceiptType().getTransactionId(latestReceiptInfo);
            final long originalTransactionId = receiptResponse.getItunesReceiptType().getOriginalTransactionId(latestReceiptInfo);
            final Optional<ReceiptDetails> receiptDetailsOption = BeanLocatorFactory.getBean(ReceiptDetailsDao.class).findById(String.valueOf(originalTransactionId));
            if(receiptDetailsOption.isPresent()) {
                final ItunesReceiptDetails receiptDetails = (ItunesReceiptDetails) receiptDetailsOption.get();
                if (receiptDetails.getTransactionId() == transactionId) {
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
            if (BeanLocatorFactory.getBean(ReceiptDetailsDao.class).existsById(receiptId)) throw new WynkRuntimeException(PaymentErrorType.PAY701);
        }
    }

}

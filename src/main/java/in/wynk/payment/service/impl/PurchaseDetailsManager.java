package in.wynk.payment.service.impl;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.PurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.IPaymentDetailsDao;
import in.wynk.payment.service.IPurchaseDetailsManger;
import in.wynk.session.dto.Session;
import in.wynk.session.service.ISessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PurchaseDetailsManager implements IPurchaseDetailsManger {

    private final IPaymentDetailsDao paymentDetailsDao;
    private final ISessionManager<String, IPurchaseDetails> sessionManager;

    @Override
    public void save(Transaction transaction, IPurchaseDetails details) {
        if (details.getPaymentDetails().isAutoRenew()) {
            paymentDetailsDao.save(PurchaseDetails.builder().id(PurchaseDetails.PurchaseKey.builder().uid(transaction.getUid()).productKey(details.getProductDetails().getId()).build()).sourceTransactionId(transaction.getIdStr()).appDetails(details.getAppDetails()).productDetails(details.getProductDetails()).paymentDetails(details.getPaymentDetails()).userDetails(details.getUserDetails()).build());
        }
        sessionManager.init(PaymentConstants.PAYMENT_DETAILS_KEY + transaction.getIdStr(), details, 3, TimeUnit.DAYS).getBody();
    }

    @Override
    public Optional<IPurchaseDetails> get(Transaction transaction) {
        if (transaction.getType() == PaymentEvent.RENEW && transaction.getStatus() != TransactionStatus.MIGRATED) {
            return paymentDetailsDao.findById(PurchaseDetails.PurchaseKey.builder().uid(transaction.getUid()).productKey(String.valueOf(transaction.getPlanId())).build());
        }
        Session<String, IPurchaseDetails> pdSession = sessionManager.get(PaymentConstants.PAYMENT_DETAILS_KEY + transaction.getIdStr());
        if (Objects.nonNull(pdSession)) {
            return Optional.of(pdSession.getBody());
        }
        return Optional.empty();
    }
}

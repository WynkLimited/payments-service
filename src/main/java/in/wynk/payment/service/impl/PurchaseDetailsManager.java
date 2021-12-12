package in.wynk.payment.service.impl;

import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.cache.constant.CacheConstant;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.RecurringDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.IRecurringDetailsDao;
import in.wynk.payment.service.IPurchaseDetailsManger;
import in.wynk.session.dto.Session;
import in.wynk.session.service.ISessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_DETAILS_KEY;

@Service
@RequiredArgsConstructor
public class PurchaseDetailsManager implements IPurchaseDetailsManger {

    private final IRecurringDetailsDao paymentDetailsDao;
    private final ISessionManager<String, IPurchaseDetails> sessionManager;

    @Override
    @CacheEvict(cacheName = "PAYMENT_DETAILS_KEY", cacheKey = "#transaction.getUid() + ':' + #transaction.getProductId()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void save(Transaction transaction, IPurchaseDetails details) {
        paymentDetailsDao.save(RecurringDetails.builder()
                .appDetails(details.getAppDetails())
                .userDetails(details.getUserDetails())
                .sourceTransactionId(transaction.getIdStr())
                .productDetails(details.getProductDetails())
                .paymentDetails(details.getPaymentDetails())
                .id(RecurringDetails.PurchaseKey.builder()
                        .uid(transaction.getUid())
                        .productKey(details.getProductDetails().getId())
                        .build())
                .build());
    }

    @Override
    @Cacheable(cacheName = "PAYMENT_DETAILS_KEY", cacheKey = "#transaction.getUid() + ':' + #transaction.getProductId()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public IPurchaseDetails get(Transaction transaction) {
        Optional<? extends IPurchaseDetails> purchaseDetails = paymentDetailsDao.findById(RecurringDetails.PurchaseKey.builder().uid(transaction.getUid()).productKey(String.valueOf(transaction.getPlanId())).build());
        if (purchaseDetails.isPresent()) return purchaseDetails.get();
        return getOldData(transaction).orElse(null);
    }

    @Deprecated
    //TODO One time hack to support data retrieval of current users till 3 days. Kindly remove it in subsequent release. Remove above added if condition also
    public Optional<? extends IPurchaseDetails> getOldData(Transaction transaction) {
        Session<String, IPurchaseDetails> pdSession = sessionManager.get(PAYMENT_DETAILS_KEY + transaction.getUid() + CacheConstant.COLON_DELIMITER + transaction.getProductId());
        if (Objects.nonNull(pdSession)) return Optional.ofNullable(pdSession.getBody());
        return Optional.empty();
    }

}
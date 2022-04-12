package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.Client;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
<<<<<<< HEAD
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.RecurringDetails;
import in.wynk.payment.core.dao.entity.Transaction;
=======
import in.wynk.payment.core.dao.entity.*;
>>>>>>> 0769c83f1a0bdc1ce3f5c3350829d5ffef3eb1a0
import in.wynk.payment.core.dao.repository.IRecurringDetailsDao;
import in.wynk.payment.core.dao.repository.receipts.IPurchasingDetailsDao;
import in.wynk.payment.service.IPurchaseDetailsManger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Service
@RequiredArgsConstructor
public class PurchaseDetailsManager implements IPurchaseDetailsManger {

    @Override
    @CacheEvict(cacheName = "PAYMENT_DETAILS_KEY", cacheKey = "#transaction.getIdStr()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void save(Transaction transaction, IPurchaseDetails details) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IPurchasingDetailsDao.class).save(PurchaseDetails.builder()
                .id(transaction.getIdStr())
                .appDetails(details.getAppDetails())
                .userDetails(details.getUserDetails())
                .productDetails(details.getProductDetails())
                .paymentDetails(details.getPaymentDetails())
                .pageUrlDetails(((IChargingDetails) details).getPageUrlDetails())
                .callbackUrl(((IChargingDetails) details).getCallbackDetails().getCallbackUrl())
                .build());
    }

    @Override
    @Cacheable(cacheName = "PAYMENT_DETAILS_KEY", cacheKey = "#transaction.getIdStr()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public IPurchaseDetails get(Transaction transaction) {
        final Optional<? extends IPurchaseDetails> purchaseDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IPurchasingDetailsDao.class).findById(transaction.getIdStr());
        if (purchaseDetails.isPresent()) return purchaseDetails.get();
        return getOldData(transaction).orElse(null);
    }

    @Deprecated
    //TODO One time hack to support data retrieval of current users till 3 days. Kindly remove it in subsequent release. Remove above added if condition also
    public Optional<? extends IPurchaseDetails> getOldData(Transaction transaction) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IRecurringDetailsDao.class).findById(RecurringDetails.PurchaseKey.builder().uid(transaction.getUid()).productKey(String.valueOf(transaction.getPlanId())).build());
    }

}
package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.Client;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.RecurringDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.IRecurringDetailsDao;
import in.wynk.payment.service.IPurchaseDetailsManger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Service
@RequiredArgsConstructor
public class PurchaseDetailsManager implements IPurchaseDetailsManger {

    @Override
    @CacheEvict(cacheName = "PAYMENT_DETAILS_KEY", cacheKey = "#transaction.getUid() + ':' + #transaction.getProductId()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void save(Transaction transaction, IPurchaseDetails details) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IRecurringDetailsDao.class).save(RecurringDetails.builder()
                .appDetails(details.getAppDetails())
                .userDetails(details.getUserDetails())
                .sourceTransactionId(transaction.getIdStr())
                .productDetails(details.getProductDetails())
                .paymentDetails(details.getPaymentDetails())
                .pageUrlDetails(((IChargingDetails) details).getPageUrlDetails())
                .callbackUrl(((IChargingDetails) details).getCallbackDetails().getCallbackUrl())
                .id(RecurringDetails.PurchaseKey.builder()
                        .uid(transaction.getUid())
                        .productKey(details.getProductDetails().getId())
                        .build())
                .build());
    }

    @Override
    @Cacheable(cacheName = "PAYMENT_DETAILS_KEY", cacheKey = "#transaction.getUid() + ':' + #transaction.getProductId()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public IPurchaseDetails get(Transaction transaction) {
        final Optional<? extends IPurchaseDetails> purchaseDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IRecurringDetailsDao.class).findById(RecurringDetails.PurchaseKey.builder().uid(transaction.getUid()).productKey(String.valueOf(transaction.getPlanId())).build());
        return purchaseDetails.get();
    }

}
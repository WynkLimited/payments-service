package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.Client;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.repository.UserPreferredPaymentsDao;
import in.wynk.payment.service.IUserPaymentsManager;
import org.springframework.stereotype.Service;

import java.util.List;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Service
public class UserPaymentsManagerImpl implements IUserPaymentsManager {

    @Override
    @Cacheable(cacheName = "UserPreferredPayment", cacheKey = "#uid", l2CacheTtl = 30 * 60, cacheManager = L2CACHE_MANAGER)
    public List<UserPreferredPayment> get(String uid) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), UserPreferredPaymentsDao.class).findAllByUid(uid);
    }

    @Override
    @CacheEvict(cacheName = "UserPreferredPayment", cacheKey = "#userPreferredPayment.getId().getUid()", l2CacheTtl = 30 * 60, cacheManager = L2CACHE_MANAGER)
    public void save(UserPreferredPayment userPreferredPayment) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), UserPreferredPaymentsDao.class).save(userPreferredPayment);
    }

    @Override
    @CacheEvict(cacheName = "UserPreferredPayment", cacheKey = "#userPreferredPayment.getId().getUid()", l2CacheTtl = 30 * 60, cacheManager = L2CACHE_MANAGER)
    public void delete(UserPreferredPayment userPreferredPayment) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), UserPreferredPaymentsDao.class).delete(userPreferredPayment);
    }

}
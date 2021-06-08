package in.wynk.payment.service.impl;

import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.payment.core.dao.entity.Key;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.repository.UserPreferredPaymentsDao;
import in.wynk.payment.service.IUserPaymentsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Service
public class UserPaymentsManagerImpl implements IUserPaymentsManager {

    @Autowired
    private UserPreferredPaymentsDao userPreferredPaymentsDao;

    @Override
    @Cacheable(cacheName = "UserPreferredPayment", cacheKey = "#key.getUid() + ':' + #key.getPaymentCode() + ':' + #key.getPaymentGroup()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public UserPreferredPayment getPaymentDetails(Key key) {
        return userPreferredPaymentsDao.findById(key).orElse(null);
    }

    @Override
    @CacheEvict(cacheName = "UserPreferredPayment", cacheKey = "#userPreferredPayment.getId().getUid() + ':' + #userPreferredPayment.getId().getPaymentCode() + ':' + #userPreferredPayment.getId().getPaymentGroup()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void savePaymentDetails(UserPreferredPayment userPreferredPayment) {
        userPreferredPaymentsDao.save(userPreferredPayment);
    }

    @Override
    @CacheEvict(cacheName = "UserPreferredPayment", cacheKey = "#userPreferredPayment.getId().getUid() + ':' + #userPreferredPayment.getId().getPaymentCode() + ':' + #userPreferredPayment.getId().getPaymentGroup()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void deletePaymentDetails(UserPreferredPayment userPreferredPayment) {
        userPreferredPaymentsDao.delete(userPreferredPayment);
    }

}
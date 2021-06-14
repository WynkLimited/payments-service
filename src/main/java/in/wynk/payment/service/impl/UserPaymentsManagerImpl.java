package in.wynk.payment.service.impl;

import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.repository.UserPreferredPaymentsDao;
import in.wynk.payment.service.IUserPaymentsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Service
public class UserPaymentsManagerImpl implements IUserPaymentsManager {

    @Autowired
    private UserPreferredPaymentsDao userPreferredPaymentsDao;

    @Override
    @Cacheable(cacheName = "UserPreferredPayment", cacheKey = "#uid", l2CacheTtl = 30 * 60, cacheManager = L2CACHE_MANAGER)
    public List<UserPreferredPayment> get(String uid) {
        return userPreferredPaymentsDao.findAllByUid(uid);
    }

    @Override
    @CacheEvict(cacheName = "UserPreferredPayment", cacheKey = "#userPreferredPayment.getId().getUid()", l2CacheTtl = 30 * 60, cacheManager = L2CACHE_MANAGER)
    public void save(UserPreferredPayment userPreferredPayment) {
        userPreferredPaymentsDao.save(userPreferredPayment);
    }

    @Override
    @CacheEvict(cacheName = "UserPreferredPayment", cacheKey = "#userPreferredPayment.getId().getUid()", l2CacheTtl = 30 * 60, cacheManager = L2CACHE_MANAGER)
    public void delete(UserPreferredPayment userPreferredPayment) {
        userPreferredPaymentsDao.delete(userPreferredPayment);
    }

}
package in.wynk.payment.service.impl;

import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Payment;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.repository.UserPreferredPaymentsDao;
import in.wynk.payment.service.IUserPaymentsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Service
public class UserPaymentsManagerImpl implements IUserPaymentsManager {

    @Autowired
    private UserPreferredPaymentsDao preferredPaymentsDao;

    @Override
    @Cacheable(cacheName = "UserPreferredPayment", cacheKey = "#uid + ':' + #paymentCode", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public UserPreferredPayment getPaymentDetails(String uid, PaymentCode paymentCode) {
        return getAllPaymentDetails(uid).stream().filter(p -> p.getOption().getPaymentCode().equals(paymentCode)).findAny().orElse(null);
    }

    @Override
    @Cacheable(cacheName = "UserPreferredPayment", cacheKey = "#uid", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public List<UserPreferredPayment> getAllPaymentDetails(String uid) {
        return preferredPaymentsDao.findByUid(uid);
    }

    @Override
    @CacheEvict(cacheName = "UserPreferredPayment", cacheKey = "#uid + ':' + #wallet.getPaymentCode()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public UserPreferredPayment saveWalletToken(String uid, Payment payment) {
        UserPreferredPayment userPreferredPayment = getPaymentDetails(uid, payment.getPaymentCode());
        if (Objects.nonNull(userPreferredPayment)) {
            userPreferredPayment.setOption(payment);
        } else {
            userPreferredPayment = UserPreferredPayment.builder().uid(uid).option(payment).build();
        }
        return preferredPaymentsDao.save(userPreferredPayment);
    }

    @Override
    @CacheEvict(cacheName = "UserPreferredPayment", cacheKey = "#uid + ':' + #paymentCode", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void deletePaymentDetails(String uid, PaymentCode paymentCode){
        UserPreferredPayment payment = getPaymentDetails(uid, paymentCode);
        preferredPaymentsDao.delete(payment);
    }

}
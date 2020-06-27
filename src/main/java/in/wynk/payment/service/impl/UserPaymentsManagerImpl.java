package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.entity.UserPreferredPayment;
import in.wynk.payment.dao.UserPreferredPaymentsDao;
import in.wynk.payment.service.IUserPaymentsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserPaymentsManagerImpl implements IUserPaymentsManager {
    @Autowired
    private UserPreferredPaymentsDao preferredPaymentsDao;

    @Override
    public Optional<UserPreferredPayment> getPaymentDetails(String uid, PaymentCode paymentCode) {
        return getAllPaymentDetails(uid).stream().filter(p -> p.getOption().getPaymentCode().equals(paymentCode)).findAny();
    }

    // can add cacheable.
    @Override
    public List<UserPreferredPayment> getAllPaymentDetails(String uid) {
        return preferredPaymentsDao.findByUid(uid);
    }
}

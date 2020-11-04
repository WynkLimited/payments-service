package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;
import in.wynk.payment.core.dao.repository.UserPreferredPaymentsDao;
import in.wynk.payment.service.IUserPaymentsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class UserPaymentsManagerImpl implements IUserPaymentsManager {
    @Autowired
    private UserPreferredPaymentsDao preferredPaymentsDao;

    @Override
    public UserPreferredPayment getPaymentDetails(String uid, PaymentCode paymentCode) {
        return getAllPaymentDetails(uid).stream()
                .filter(p -> p.getOption().getPaymentCode().equals(paymentCode)).findAny().orElse(null);
    }

    // can add cacheable.
    @Override
    public List<UserPreferredPayment> getAllPaymentDetails(String uid) {
        return preferredPaymentsDao.findByUid(uid);
    }
    //might need to update cache
    @Override
    public UserPreferredPayment saveWalletToken(String uid, Wallet wallet) {
        UserPreferredPayment userPreferredPayment = getPaymentDetails(uid, wallet.getPaymentCode());
        if (Objects.nonNull(userPreferredPayment)) {
            userPreferredPayment.setOption(wallet);
        } else {
            userPreferredPayment = UserPreferredPayment.builder().uid(uid).option(wallet).build();
        }
        return preferredPaymentsDao.save(userPreferredPayment);
    }

    public void deletePaymentDetails(String uid, PaymentCode paymentCode){
        UserPreferredPayment payment = getPaymentDetails(uid, paymentCode);
        preferredPaymentsDao.delete(payment);
    }
}

package in.wynk.payment.dao;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.entity.UserPreferredPayment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPreferredPaymentsDao extends MongoRepository<UserPreferredPayment, String> {
    List<UserPreferredPayment> findByUid(String uid);
    List<UserPreferredPayment> findByUidPaymentCode(String uid, PaymentCode paymentCode);
}

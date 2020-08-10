package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPreferredPaymentsDao extends MongoRepository<UserPreferredPayment, String> {

    List<UserPreferredPayment> findByUid(String uid);
}

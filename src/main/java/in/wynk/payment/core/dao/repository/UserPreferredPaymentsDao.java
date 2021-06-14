package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.dao.entity.SavedDetailsKey;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPreferredPaymentsDao extends MongoRepository<UserPreferredPayment, SavedDetailsKey> {

    @Query("{'_id.uid' : ?0}")
    List<UserPreferredPayment> findAllByUid(String uid);

}
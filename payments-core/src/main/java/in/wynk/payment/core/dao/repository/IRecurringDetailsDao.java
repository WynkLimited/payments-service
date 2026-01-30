package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.dao.entity.RecurringDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Deprecated
@Repository
public interface IRecurringDetailsDao extends MongoRepository<RecurringDetails, RecurringDetails.PurchaseKey> {
}

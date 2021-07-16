package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.dao.entity.PurchaseDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IPaymentDetailsDao extends MongoRepository<PurchaseDetails, PurchaseDetails.PurchaseKey> {
}

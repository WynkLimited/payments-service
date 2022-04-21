package in.wynk.payment.core.dao.repository.receipts;

import in.wynk.payment.core.dao.entity.PurchaseDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IPurchasingDetailsDao extends MongoRepository<PurchaseDetails, String> {

}

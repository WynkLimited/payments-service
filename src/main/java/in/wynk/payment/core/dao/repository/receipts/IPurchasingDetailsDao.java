package in.wynk.payment.core.dao.repository.receipts;

import in.wynk.payment.core.dao.entity.PurchaseDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IPurchasingDetailsDao extends MongoRepository<PurchaseDetails, String> {
    @Query("{ 'payer_details.msisdn' : ?0 }")
    <T extends PurchaseDetails> List<T> findByUserId(String userId);

}

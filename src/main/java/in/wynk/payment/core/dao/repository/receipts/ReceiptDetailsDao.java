package in.wynk.payment.core.dao.repository.receipts;

import in.wynk.payment.core.dao.entity.ReceiptDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceiptDetailsDao extends MongoRepository<ReceiptDetails, String> {

    @Query("{ 'planId' : ?0, '_id' : ?1 }")
    <T extends ReceiptDetails> T findByPlanIdAndId(int planId, String id);

//    <T extends ReceiptDetails> T findById(String itunesId);

}

package in.wynk.payment.core.dao.repository.receipts;

import in.wynk.payment.core.dao.entity.ReceiptDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReceiptDetailsDao extends MongoRepository<ReceiptDetails, String> {

    @Query("{ 'planId' : ?0, '_id' : ?1 }")
    <T extends ReceiptDetails> T findByPlanIdAndId(int planId, String id);

    @Query("{'uid': ?0, 'planId': ?1}")
    <T extends ReceiptDetails> T findByUidAndPlanId(String uid, int planId);

    @Query("{'paymentTransactionId': ?0}")
    <T extends ReceiptDetails> T findByPaymentTransactionId(String transactionId);

    @Query("{'uid': ?0}")
    <T extends ReceiptDetails> List<T> findByUid(String uid);

}

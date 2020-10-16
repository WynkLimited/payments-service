package in.wynk.payment.core.dao.repository.receipts;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmazonIdUidDao extends MongoRepository<AmazonIdUidDao, String> {
}

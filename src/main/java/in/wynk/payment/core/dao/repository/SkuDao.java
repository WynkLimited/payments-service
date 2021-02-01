package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.dao.entity.SkuMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SkuDao extends MongoRepository<SkuMapping, String> {
}

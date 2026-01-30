package in.wynk.payment.core.dao.repository;

import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.SkuMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkuDao extends MongoRepository<SkuMapping, String> {
    List<SkuMapping> findAllByState(State state);
}

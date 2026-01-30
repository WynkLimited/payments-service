package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.dao.entity.TestingByPassNumbers;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestingByPassNumbersDao extends MongoRepository<TestingByPassNumbers, String> {
}

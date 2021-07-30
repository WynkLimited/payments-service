package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.dao.entity.WynkUserExtUserMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WynkUserExtUserDao extends MongoRepository<WynkUserExtUserMapping, String> {

    Long countByExternalUserId(String externalUserId);

    WynkUserExtUserMapping findByExternalUserId(String externalUserId);
}

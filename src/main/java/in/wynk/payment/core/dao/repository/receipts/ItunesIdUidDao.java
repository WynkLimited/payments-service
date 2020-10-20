package in.wynk.payment.core.dao.repository.receipts;

import in.wynk.payment.core.dao.entity.ItunesIdUidMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Deprecated
@Repository
public interface ItunesIdUidDao extends MongoRepository<ItunesIdUidMapping, String> {

    @Query("{ 'planId' : ?0, '_id' : ?1 }")
    ItunesIdUidMapping findByPlanIdAndItunesId(int planId, String itunesId);

    ItunesIdUidMapping findByItunesId(String itunesId);
}
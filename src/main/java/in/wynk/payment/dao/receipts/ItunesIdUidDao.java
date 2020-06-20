package in.wynk.payment.dao.receipts;

import in.wynk.payment.dao.entity.ItunesIdUidMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ItunesIdUidDao extends MongoRepository<ItunesIdUidMapping, ItunesIdUidMapping.Key> {

    @Query("{'_id.productId : ?0','itunesId : ?1'}")
    ItunesIdUidMapping findByProductAndItunesId(int productId, String itunesId);


}
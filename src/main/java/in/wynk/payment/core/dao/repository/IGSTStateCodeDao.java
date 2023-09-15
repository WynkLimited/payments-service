package in.wynk.payment.core.dao.repository;

import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.GSTStateCodes;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IGSTStateCodeDao extends MongoRepository<GSTStateCodes, String> {
    List<GSTStateCodes> findAllByState(State state);
}
package in.wynk.payment.core.dao.repository;

import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.InvoiceSequence;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InvoiceSequenceDao extends MongoRepository<InvoiceSequence, String> {
    List<InvoiceSequence> findAllByState(State state);
}

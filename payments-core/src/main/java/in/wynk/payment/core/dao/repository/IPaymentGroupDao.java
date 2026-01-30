package in.wynk.payment.core.dao.repository;

import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IPaymentGroupDao extends MongoRepository<PaymentGroup, String> {

    List<PaymentGroup> findAllByState(State state);

}

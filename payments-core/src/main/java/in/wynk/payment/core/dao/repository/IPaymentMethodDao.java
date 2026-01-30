package in.wynk.payment.core.dao.repository;

import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface IPaymentMethodDao extends MongoRepository<PaymentMethod, String> {

    List<PaymentMethod> findAllByState(State state);

    List<PaymentMethod> findByGroupInAndState(Collection<String> groups, State state);

}

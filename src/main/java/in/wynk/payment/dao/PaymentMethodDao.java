package in.wynk.payment.dao;

import in.wynk.commons.enums.State;
import in.wynk.payment.core.entity.PaymentMethod;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentMethodDao extends MongoRepository<PaymentMethod, String> {

    List<PaymentMethod> findAllByState(State state);
}

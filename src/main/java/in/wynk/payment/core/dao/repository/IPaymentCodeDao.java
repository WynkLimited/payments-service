package in.wynk.payment.core.dao.repository;

import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IPaymentCodeDao extends MongoRepository<PaymentGateway, String> {
    List<PaymentGateway> findAllByState(State state);
}
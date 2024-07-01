package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.dao.entity.PaymentRenewalDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRenewalDetailsDao extends MongoRepository<PaymentRenewalDetails, String> {
}

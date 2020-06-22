package in.wynk.payment.dao;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dto.PaymentRenewal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository(BeanConstant.PAYMENT_RENEWAL_DAO)
public interface IPaymentRenewalDao extends JpaRepository<PaymentRenewal, UUID> {
}

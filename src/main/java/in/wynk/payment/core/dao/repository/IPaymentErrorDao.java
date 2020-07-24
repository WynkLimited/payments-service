package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.PaymentError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository(BeanConstant.PAYMENT_ERROR_DAO)
public interface IPaymentErrorDao extends JpaRepository<PaymentError, String> {
}

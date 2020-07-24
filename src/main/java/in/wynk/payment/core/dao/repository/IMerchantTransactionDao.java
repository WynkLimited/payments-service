package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository(BeanConstant.MERCHANT_TRANSACTION_DAO)
public interface IMerchantTransactionDao extends JpaRepository<MerchantTransaction, String> {
}

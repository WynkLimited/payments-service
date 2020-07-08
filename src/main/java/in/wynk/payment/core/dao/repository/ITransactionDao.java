package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository(BeanConstant.TRANSACTION_DAO)
public interface ITransactionDao extends JpaRepository<Transaction, String> {

}
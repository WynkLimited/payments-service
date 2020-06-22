package in.wynk.payment.dao;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dto.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository(BeanConstant.TRANSACTION_DAO)
public interface ITransactionDao extends JpaRepository<Transaction, UUID> {
}
package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository(BeanConstant.TRANSACTION_DAO)
public interface ITransactionDao extends JpaRepository<Transaction, String> {
    @Query(value="SELECT * FROM payments.transaction WHERE init_timestamp >= :fromDate AND init_timestamp <= :toDate", nativeQuery = true)
    List<Transaction> getTransactionWeeklyDump(@Param("fromDate") Date fromDate, @Param("toDate") Date toDate);

}
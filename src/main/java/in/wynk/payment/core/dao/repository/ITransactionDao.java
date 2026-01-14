package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository(BeanConstant.TRANSACTION_DAO)
public interface ITransactionDao extends JpaRepository<Transaction, String> {
    @Query("FROM Transaction WHERE init_timestamp >= :fromDate AND init_timestamp <= :toDate")
    Stream<Transaction> getTransactionDailyDump(@Param("fromDate") Date fromDate, @Param("toDate") Date toDate);
    @Query("FROM Transaction WHERE original_transaction_id = :originalTxnId")
    Optional<Transaction> findByOriginalTxnId(@Param("originalTxnId") String originalTxnId);
    List<Transaction> findAllByUidAndClientAliasOrderByUpdatedAtDesc(String uid, String clientAlias, Pageable pageable);
}
package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.PaymentTDRDetails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Repository("paymentTdrDetailsDao")
@Primary
public interface PaymentTDRDetailsDao extends JpaRepository<PaymentTDRDetails, String> {

    Logger logger = LoggerFactory.getLogger(PaymentTDRDetailsDao.class);

    @Query(value = "SELECT * FROM tdr_details t " +
            "WHERE t.is_processed = 0 " +
            "AND t.execution_time <= :currentTimestamp " +
            "ORDER BY t.execution_time ASC " +
            "LIMIT 1",
            nativeQuery = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    Optional<PaymentTDRDetails> findFirstEligibleTransaction(@Param("currentTimestamp") Date currentTimestamp);

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    default Optional<PaymentTDRDetails> fetchNextTransactionForProcessing() {
        try {
            return findFirstEligibleTransaction(new Date());
        } catch (DataAccessException e) {
            logger.error(PaymentLoggingMarker.TDR_TABLE_FETCHING_QUERY_ERROR,
                    "Error while fetching first Eligible Transaction from tdr_table",
                    e);
            return Optional.empty();
        }
    }
}

//@Repository("paymentTdrDetailsDao")
//@Primary
//public interface PaymentTDRDetailsDao extends JpaRepository<PaymentTDRDetails, String> {
//
//    @Query("SELECT t FROM PaymentTDRDetails t " +
//            "WHERE t.isProcessed = false " +
//            "AND t.executionTime <= :currentTimestamp " +
//            "ORDER BY t.executionTime ASC")
////    List<PaymentTDRDetails> findEligibleTransactions(@Param("currentTimestamp") Date currentTimestamp);
//@Query(value = "SELECT t FROM PaymentTDRDetails t " +
//        "WHERE t.isProcessed = false " +
//        "AND t.executionTime <= :currentTimestamp " +
//        "ORDER BY t.executionTime ASC " +
//        "LIMIT 1",
//        nativeQuery = true)
//@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
//Optional<PaymentTDRDetails> findFirstEligibleTransactionNative(@Param("currentTimestamp") Date currentTimestamp);
//
//    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
//    default Optional<PaymentTDRDetails> fetchNextTransactionForProcessing() {
//        try {
//            Date now = new Date();
//            List<PaymentTDRDetails> results = findEligibleTransactions(now);
//            return results.stream().findFirst();
//        } catch (DataAccessException e) {
//            System.err.println("Database error: " + e.getMessage());
//            return Optional.empty();
//        }
//    }
//}




//@Repository("paymentTdrDetailsDao")
//@Primary
//public interface PaymentTDRDetailsDao extends JpaRepository<PaymentTDRDetails, String> {
//
//    @Query(value = "SELECT t FROM PaymentTDRDetails t " +
//            "WHERE t.isProcessed = false " +
//            "AND t.executionTime <= CURRENT_TIMESTAMP " +
//            "ORDER BY t.executionTime ASC")
//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    List<PaymentTDRDetails> findNextTransactionsForProcessing(Pageable pageable);
//
//    default Optional<PaymentTDRDetails> fetchNextTransactionForProcessing() {
//        List<PaymentTDRDetails> results = findNextTransactionsForProcessing(PageRequest.of(0, 1));
//        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
//    }
//}

//@Repository("paymentTdrDetailsDao")
//@Primary
//public interface PaymentTDRDetailsDao extends JpaRepository<PaymentTDRDetails, String> {
//
//    @Query(value = "SELECT t FROM PaymentTDRDetails t " +
//            "WHERE t.isProcessed = false " +
//            "AND t.executionTime <= CURRENT_TIMESTAMP " +
//            "ORDER BY t.executionTime ASC")
//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Transactional
//    List<PaymentTDRDetails> findNextTransactionsForProcessing(Pageable pageable);
//
//    @Transactional
//    default Optional<PaymentTDRDetails> fetchNextTransactionForProcessing() {
//        List<PaymentTDRDetails> results = findNextTransactionsForProcessing(PageRequest.of(0, 1));
//        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
//    }
//}


//@Repository("paymentTdrDetailsDao")
//@Primary
//public interface PaymentTDRDetailsDao extends JpaRepository<PaymentTDRDetails, String> {
//
//    @Query(value = "SELECT t FROM PaymentTDRDetails t " +
//            "WHERE t.isProcessed = false " +
//            "AND t.executionTime <= CURRENT_TIMESTAMP " +
//            "ORDER BY t.executionTime ASC")
//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Transactional(
//            propagation = Propagation.REQUIRES_NEW,
//            timeout = 30,  // Increased timeout for pessimistic lock acquisition
//            isolation = Isolation.READ_COMMITTED
//    )
//    List<PaymentTDRDetails> findNextTransactionsForProcessing(Pageable pageable);
//
//    @Transactional(
//            propagation = Propagation.REQUIRES_NEW,
//            timeout = 30
//    )
//    default Optional<PaymentTDRDetails> fetchNextTransactionForProcessing() {
//        try {
//            List<PaymentTDRDetails> results = findNextTransactionsForProcessing(PageRequest.of(0, 1));
//            return results.stream().findFirst();
//        } catch (DataAccessResourceFailureException e) {
//
//            return Optional.empty();
//        }
//    }
//}
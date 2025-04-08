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
            "LIMIT 1 FOR UPDATE SKIP LOCKED",
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
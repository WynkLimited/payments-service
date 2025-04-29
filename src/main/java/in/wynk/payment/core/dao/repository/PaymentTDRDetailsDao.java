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

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Repository("paymentTdrDetailsDao")
@Primary
public interface PaymentTDRDetailsDao extends JpaRepository<PaymentTDRDetails, String> {

    Logger logger = LoggerFactory.getLogger(PaymentTDRDetailsDao.class);

    @Query(value = "SELECT * FROM tdr_details t " +
            "WHERE t.status = 'PENDING' " +
            "AND t.execution_time <= :currentTimestamp " +
            "ORDER BY t.execution_time ASC " +
            "LIMIT :batchSize",
            nativeQuery = true)
    @Transactional(propagation = Propagation.REQUIRED, timeout = 60)
    List<PaymentTDRDetails> findNextBatchForProcessing(@Param("currentTimestamp") Date currentTimestamp,
                                                       @Param("batchSize") int batchSize);

    @Transactional(propagation = Propagation.REQUIRED, timeout = 60)
    default List<PaymentTDRDetails> fetchNextTransactionForProcessing(int batchSize) {
        try {
            return findNextBatchForProcessing(new Date(), batchSize);
        } catch (DataAccessException e) {
            logger.error(PaymentLoggingMarker.TDR_TABLE_FETCHING_QUERY_ERROR,
                    "Error while fetching eligible transactions from tdr_table",
                    e);
        } catch (Exception e) {
            logger.error(PaymentLoggingMarker.TDR_TABLE_FETCHING_QUERY_ERROR,
                    "Unexpected error while fetching eligible transactions from tdr_table",
                    e);
        }
        return Collections.emptyList();
    }
}
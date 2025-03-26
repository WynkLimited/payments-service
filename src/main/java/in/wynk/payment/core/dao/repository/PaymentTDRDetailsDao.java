package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.Invoice;
import in.wynk.payment.core.dao.entity.PaymentTDRDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository(BeanConstant.PAYMENT_TDR_DETAILS)
public interface PaymentTDRDetailsDao extends JpaRepository<PaymentTDRDetails, String> {

    @Query("SELECT t FROM PaymentTDRDetails t WHERE t.isProcessed = false " +
            "AND t.executionTime <= CURRENT_TIMESTAMP " +
            "ORDER BY t.executionTime ASC " +
            "LIMIT 1 FOR UPDATE SKIP LOCKED")
    Optional<PaymentTDRDetails> fetchNextTransactionForProcessing();


}

package in.wynk.payment.core.dao.repository;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository(BeanConstant.INVOICE_DAO)
public interface InvoiceDao extends JpaRepository<Invoice, String> {
    Optional<Invoice> findById(@Param("id") String id);
    Optional<Invoice> findByTransactionId(@Param("transaction_id") String txnId);
}

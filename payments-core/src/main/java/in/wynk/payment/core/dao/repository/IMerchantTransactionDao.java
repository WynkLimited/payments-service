package in.wynk.payment.core.dao.repository;

import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository(BeanConstant.MERCHANT_TRANSACTION_DAO)
public interface IMerchantTransactionDao extends JpaRepository<MerchantTransaction, String> {

    Optional<String> findPartnerReferenceById(@Param(BaseConstants.TRANSACTION_ID) String id);

    Optional<String> findTransactionIdByOrderId(@Param("orderId") String orderId);

    Optional<MerchantTransaction> findMerchantTransactionByExternalTransactionId(@Param("externalTransactionId") String externalTransactionId);
}

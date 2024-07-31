package in.wynk.payment.service;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
public interface IMerchantTransactionService {
    void upsert(MerchantTransaction merchantTransaction);
    MerchantTransaction getMerchantTransaction(String id);
    String getPartnerReferenceId(String id);
    String findTransactionId (String orderId);
    String findTransactionIdByExternalTransactionId (String externalTransactionId);
}

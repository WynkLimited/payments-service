package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;

import java.util.Optional;

public interface IPurchaseDetailsManger {

    void save(Transaction transaction, IPurchaseDetails details);

    Optional<IPurchaseDetails> get(Transaction transaction);

}

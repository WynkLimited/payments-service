package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;

public interface IPurchaseDetailsManger {

    IPurchaseDetails get(Transaction transaction);

    void save(Transaction transaction, IPurchaseDetails details);

}
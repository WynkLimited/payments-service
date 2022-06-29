package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;

import java.util.List;
import java.util.Set;

public interface IPurchaseDetailsManger {

    IPurchaseDetails get(Transaction transaction);

    void save(Transaction transaction, IPurchaseDetails details);

    Set<String> getByUserId(String userId);

}
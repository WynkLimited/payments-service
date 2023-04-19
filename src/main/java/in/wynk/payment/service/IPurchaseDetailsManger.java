package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;

import java.util.List;

public interface IPurchaseDetailsManger {

    IPurchaseDetails get(Transaction transaction);

    void save(Transaction transaction, IPurchaseDetails details);

    void save(Transaction transaction, AbstractPaymentChargingRequest details);

    List<String> getByUserId(String userId);

}
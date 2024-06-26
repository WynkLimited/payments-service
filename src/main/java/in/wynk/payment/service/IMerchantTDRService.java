package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Transaction;

public interface IMerchantTDRService {
    Double getTDR (Transaction transaction);
}

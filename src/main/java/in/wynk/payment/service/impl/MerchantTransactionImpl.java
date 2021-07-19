package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.repository.IMerchantTransactionDao;
import in.wynk.payment.service.IMerchantTransactionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MerchantTransactionImpl implements IMerchantTransactionService {

    private final IMerchantTransactionDao merchantTransactionDao;

    public MerchantTransactionImpl(@Qualifier(BeanConstant.MERCHANT_TRANSACTION_DAO) IMerchantTransactionDao merchantTransactionDao) {
        this.merchantTransactionDao = merchantTransactionDao;
    }

    @Override
    public void upsert(MerchantTransaction merchantTransaction) {
        merchantTransactionDao.save(merchantTransaction);
    }

    @Override
    public MerchantTransaction getMerchantTransaction(String id) {
        return merchantTransactionDao.findById(id).get();
    }

    @Override
    public String getPartnerReferenceId(String id) {
        return merchantTransactionDao.findPartnerReferenceById(id).get();
    }

}

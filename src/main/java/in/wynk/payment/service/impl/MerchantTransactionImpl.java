package in.wynk.payment.service.impl;

import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.repository.IMerchantTransactionDao;
import in.wynk.payment.service.IMerchantTransactionService;
import org.springframework.stereotype.Service;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY108;

@Service
public class MerchantTransactionImpl implements IMerchantTransactionService {

    @Override
    public void upsert(MerchantTransaction merchantTransaction) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(PAY108)).getAlias(), IMerchantTransactionDao.class).save(merchantTransaction);
    }

    @Override
    public MerchantTransaction getMerchantTransaction(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(PAY108)).getAlias(), IMerchantTransactionDao.class).findById(id).get();
    }

    @Override
    public String getPartnerReferenceId(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(PAY108)).getAlias(), IMerchantTransactionDao.class).findPartnerReferenceById(id).get();
    }

}
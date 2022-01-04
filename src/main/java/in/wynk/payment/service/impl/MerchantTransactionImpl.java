package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.repository.IMerchantTransactionDao;
import in.wynk.payment.service.IMerchantTransactionService;
import org.springframework.stereotype.Service;

@Service
public class MerchantTransactionImpl implements IMerchantTransactionService {

    @Override
    public void upsert(MerchantTransaction merchantTransaction) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse("paymentApi"), IMerchantTransactionDao.class).save(merchantTransaction);
    }

    @Override
    public MerchantTransaction getMerchantTransaction(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse("paymentApi"), IMerchantTransactionDao.class).findById(id).get();
    }

    @Override
    public String getPartnerReferenceId(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse("paymentApi"), IMerchantTransactionDao.class).findPartnerReferenceById(id).get();
    }

}
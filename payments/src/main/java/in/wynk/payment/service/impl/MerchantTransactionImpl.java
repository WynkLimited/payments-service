package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.repository.IMerchantTransactionDao;
import in.wynk.payment.service.IMerchantTransactionService;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Service
public class MerchantTransactionImpl implements IMerchantTransactionService {

    @Override
    public void upsert(MerchantTransaction merchantTransaction) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IMerchantTransactionDao.class).save(merchantTransaction);
    }

    @Override
    public MerchantTransaction getMerchantTransaction(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IMerchantTransactionDao.class).findById(id).orElse(null);
    }



    @Override
    public String getPartnerReferenceId(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IMerchantTransactionDao.class).findPartnerReferenceById(id).orElse(null);
    }

    @Override
    public String findTransactionId(String orderId) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IMerchantTransactionDao.class).findTransactionIdByOrderId(orderId).get();
    }

    @Override
    public String findTransactionIdByExternalTransactionId (String externalTransactionId) {
        MerchantTransaction merchantTransaction = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IMerchantTransactionDao.class)
                .findMerchantTransactionByExternalTransactionId(externalTransactionId).orElse(null);
        if (Objects.nonNull(merchantTransaction)) {
            return merchantTransaction.getId();
        }
        return null;
    }

}
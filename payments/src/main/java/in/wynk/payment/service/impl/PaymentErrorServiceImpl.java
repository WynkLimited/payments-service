package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.repository.IPaymentErrorDao;
import in.wynk.payment.service.IPaymentErrorService;
import org.springframework.stereotype.Service;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Service
public class PaymentErrorServiceImpl implements IPaymentErrorService {

    @Override
    public void upsert(PaymentError error) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentErrorDao.class).save(error);
    }

    @Override
    public PaymentError getPaymentError(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentErrorDao.class).findById(id).get();
    }

}
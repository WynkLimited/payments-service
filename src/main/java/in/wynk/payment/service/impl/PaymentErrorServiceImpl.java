package in.wynk.payment.service.impl;

import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.repository.IPaymentErrorDao;
import in.wynk.payment.service.IPaymentErrorService;
import org.springframework.stereotype.Service;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY108;

@Service
public class PaymentErrorServiceImpl implements IPaymentErrorService {

    @Override
    public void upsert(PaymentError error) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(PAY108)).getAlias(), IPaymentErrorDao.class).save(error);
    }

    @Override
    public PaymentError getPaymentError(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(PAY108)).getAlias(), IPaymentErrorDao.class).findById(id).get();
    }

}
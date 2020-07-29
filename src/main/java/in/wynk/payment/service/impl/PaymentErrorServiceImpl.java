package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.repository.IPaymentErrorDao;
import in.wynk.payment.service.IPaymentErrorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class PaymentErrorServiceImpl implements IPaymentErrorService {

    private final IPaymentErrorDao paymentErrorDao;

    public PaymentErrorServiceImpl(@Qualifier(BeanConstant.PAYMENT_ERROR_DAO) IPaymentErrorDao paymentErrorDao) {
        this.paymentErrorDao = paymentErrorDao;
    }

    @Override
    public void upsert(PaymentError error) {
        paymentErrorDao.save(error);
    }

    @Override
    public PaymentError getPaymentError(String id) {
        return paymentErrorDao.findById(id).get();
    }
}

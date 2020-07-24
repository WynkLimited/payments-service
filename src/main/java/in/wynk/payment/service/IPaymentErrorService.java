package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.PaymentError;

public interface IPaymentErrorService {

    void upsert(PaymentError error);

    PaymentError getPaymentError(String id);

}

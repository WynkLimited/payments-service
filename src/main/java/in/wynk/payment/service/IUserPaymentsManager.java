package in.wynk.payment.service;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Payment;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;

import java.util.List;

public interface IUserPaymentsManager {

    UserPreferredPayment getPaymentDetails(String uid, PaymentCode paymentCode);

    List<UserPreferredPayment> getAllPaymentDetails(String uid);

    UserPreferredPayment saveWalletToken(String uid, Payment payment);

    void deletePaymentDetails(String uid, PaymentCode paymentCode);

}
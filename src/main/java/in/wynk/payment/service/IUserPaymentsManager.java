package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;
import in.wynk.payment.core.enums.PaymentCode;

import java.util.List;

public interface IUserPaymentsManager {

    UserPreferredPayment getPaymentDetails(String uid, PaymentCode paymentCode);

    List<UserPreferredPayment> getAllPaymentDetails(String uid);

    UserPreferredPayment saveWalletToken(String uid, Wallet wallet);

    void deletePaymentDetails(String uid, PaymentCode paymentCode);
}

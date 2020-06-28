package in.wynk.payment.service;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.entity.UserPreferredPayment;
import in.wynk.payment.core.entity.Wallet;

import java.util.List;
import java.util.Optional;

public interface IUserPaymentsManager {

    Optional<UserPreferredPayment> getPaymentDetails(String uid, PaymentCode paymentCode);

    List<UserPreferredPayment> getAllPaymentDetails(String uid);

    UserPreferredPayment saveWalletToken(String uid, Wallet wallet);
}

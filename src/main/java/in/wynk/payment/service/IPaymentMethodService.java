package in.wynk.payment.service;

import in.wynk.commons.dto.SessionDTO;
import in.wynk.payment.dto.PaymentMethods;

public interface IPaymentMethodService {

    PaymentMethods getPaymentOptions(SessionDTO sessionDTO, String planId);
}

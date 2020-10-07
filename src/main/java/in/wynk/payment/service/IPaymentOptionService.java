package in.wynk.payment.service;

import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.dto.response.PaymentOptionsDTO;

public interface IPaymentOptionService {

    PaymentOptionsDTO getPaymentOptions(SessionDTO sessionDTO, String planId);
}

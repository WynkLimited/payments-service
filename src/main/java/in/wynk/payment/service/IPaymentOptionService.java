package in.wynk.payment.service;

import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.response.PaymentOptionsDTO;

public interface IPaymentOptionService {

    PaymentOptionsDTO getPaymentOptions(String planId, String itemId);
    PaymentOptionsDTO getFilteredPaymentOptions(AbstractPaymentOptionsRequest<?> request);

}
package in.wynk.payment.service;

import in.wynk.payment.dto.request.PaymentOptionsRequest;
import in.wynk.payment.dto.response.PaymentOptionsDTO;

public interface IPaymentOptionService {

    PaymentOptionsDTO getPaymentOptions(String planId, String itemId);
    PaymentOptionsDTO getFilteredPaymentOptions(PaymentOptionsRequest request);

}
package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.response.PaymentOptionsDTO;

public interface IPaymentOptionService {

    WynkResponseEntity<PaymentOptionsDTO>  getPaymentOptions(String planId, String itemId);
    WynkResponseEntity<PaymentOptionsDTO> getFilteredPaymentOptions(AbstractPaymentOptionsRequest<?> request);

}
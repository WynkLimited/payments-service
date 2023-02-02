package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.response.paymentoption.PaymentOptionsDTO;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentOptionServiceV2 {
    PaymentOptionsDTO getPaymentOptions(AbstractPaymentOptionsRequest<?> request);
}

package in.wynk.payment.service;

import in.wynk.payment.dto.common.FilteredPaymentOptionsResult;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.response.paymentoption.PaymentOptionsDTO;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentOptionServiceV2 {
    FilteredPaymentOptionsResult getPaymentOptions(AbstractPaymentOptionsRequest<?> request);

    FilteredPaymentOptionsResult getPaymentOptionsForQRCode(AbstractPaymentOptionsRequest<?> request);
}

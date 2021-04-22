package in.wynk.payment.service;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.response.PaymentDetailsWrapper;
import in.wynk.payment.dto.response.PaymentOptionsDTO;

import java.util.List;

public interface IPaymentOptionService {

    PaymentOptionsDTO getPaymentOptions(String planId);

    PaymentDetailsWrapper getPaymentDetails(String planId, List<PaymentCode> codes);

}

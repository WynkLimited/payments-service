package in.wynk.payment.service.impl;

import in.wynk.commons.dto.SessionDTO;
import in.wynk.payment.dto.PaymentMethods;
import in.wynk.payment.service.IPaymentMethodService;
import org.springframework.stereotype.Service;

@Service
public class PaymentMethodServiceImpl implements IPaymentMethodService {
    @Override
    public PaymentMethods getPaymentOptions(SessionDTO sessionDTO, String planId) {

        return null;
    }
}

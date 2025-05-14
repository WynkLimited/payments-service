package in.wynk.payment.service.impl;

import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.IPaymentRenewalInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentRenewalInfoServiceImpl implements IPaymentRenewalInfoService {

    private final IRecurringPaymentManagerService recurringPaymentManagerService;

    @Override
    public String getMerchantTransactionEvent(String transactionId) {
        PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(transactionId);
        return renewal != null && renewal.getTransactionEvent() != null
                ? renewal.getTransactionEvent().name()
                : null;
    }
}


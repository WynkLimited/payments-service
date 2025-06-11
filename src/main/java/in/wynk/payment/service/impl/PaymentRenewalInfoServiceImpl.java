package in.wynk.payment.service.impl;

import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.IPaymentRenewalInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentLoggingMarker.GET_MERCHANT_EVENT;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRenewalInfoServiceImpl implements IPaymentRenewalInfoService {

    private final IRecurringPaymentManagerService recurringPaymentManagerService;

    @Override
    public String getMerchantTransactionEvent(String transactionId) {
        try {
            PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(transactionId);

            if (Objects.nonNull(renewal) && Objects.nonNull(renewal.getTransactionEvent())) {
                return renewal.getTransactionEvent().name();
            }
            return "NA";
        } catch (Exception e) {
            log.error(GET_MERCHANT_EVENT, "ERROR fetching renewal entry, transactionId={}", transactionId, e);
            throw e;
        }
    }
}
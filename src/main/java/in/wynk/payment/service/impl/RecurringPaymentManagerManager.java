package in.wynk.payment.service.impl;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import org.apache.commons.lang.time.DateUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

@Service(BeanConstant.RECURRING_PAYMENT_RENEWAL_SERVICE)
public class RecurringPaymentManagerManager implements IRecurringPaymentManagerService {

    @Value("${payment.recurring.offset.day}")
    private int dueRecurringOffsetDay;
    @Value("${payment.recurring.offset.hour}")
    private int dueRecurringOffsetTime;

    private final IPaymentRenewalDao paymentRenewalDao;
    private final ApplicationEventPublisher eventPublisher;

    public RecurringPaymentManagerManager(@Qualifier(BeanConstant.PAYMENT_RENEWAL_DAO) IPaymentRenewalDao paymentRenewalDao,
                                          ApplicationEventPublisher eventPublisher) {
        this.paymentRenewalDao = paymentRenewalDao;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PaymentRenewal addRecurringPayment(String transactionId, Calendar nextRecurringDateTime) {
        return paymentRenewalDao.save(PaymentRenewal.builder()
                                                    .transactionId(transactionId)
                                                    .day(nextRecurringDateTime)
                                                    .hour(nextRecurringDateTime.getTime())
                                                    .transactionEvent(TransactionEvent.SUBSCRIBE.name())
                                                    .createdTimestamp(Calendar.getInstance())
                                                    .build());
    }

    @Override
    public Stream<PaymentRenewal> getCurrentDueRecurringPayments() {
        Calendar currentDay = Calendar.getInstance();
        Calendar currentDayWithOffset = Calendar.getInstance();
        Date currentTime = currentDay.getTime();
        Date currentTimeWithOffset = currentDayWithOffset.getTime();
        DateUtils.addHours(currentTimeWithOffset, dueRecurringOffsetTime);
        currentDayWithOffset.add(Calendar.DAY_OF_MONTH, dueRecurringOffsetDay);
        return paymentRenewalDao.getRecurrentPayment(currentDay, currentDayWithOffset, currentTime, currentTimeWithOffset);
    }

    @Override
    public void unScheduleRecurringPayment(UUID transactionId) {
        paymentRenewalDao.findById(transactionId).ifPresent(recurringPayment -> {
            recurringPayment.setTransactionEvent(TransactionEvent.UNSUBSCRIBE.name());
            recurringPayment.setUpdatedTimestamp(Calendar.getInstance());
            paymentRenewalDao.save(recurringPayment);
            eventPublisher.publishEvent(RecurringPaymentEvent.builder()
                          .transactionId(transactionId)
                          .transactionEvent(TransactionEvent.UNSUBSCRIBE)
                          .build());
        });
    }

}

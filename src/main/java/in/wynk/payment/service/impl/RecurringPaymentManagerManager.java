package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import org.apache.commons.lang.time.DateUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.stream.Stream;

import static in.wynk.common.constant.BaseConstants.MIGRATED_NEXT_CHARGING_DATE;

@Service(BeanConstant.RECURRING_PAYMENT_RENEWAL_SERVICE)
public class RecurringPaymentManagerManager implements IRecurringPaymentManagerService {

    @Value("${payment.recurring.offset.day}")
    private int dueRecurringOffsetDay;
    @Value("${payment.recurring.offset.hour}")
    private int dueRecurringOffsetTime;

    private final IPaymentRenewalDao paymentRenewalDao;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCachingService paymentCachingService;

    public RecurringPaymentManagerManager(@Qualifier(BeanConstant.PAYMENT_RENEWAL_DAO) IPaymentRenewalDao paymentRenewalDao,
                                          ApplicationEventPublisher eventPublisher, PaymentCachingService paymentCachingService) {
        this.paymentRenewalDao = paymentRenewalDao;
        this.eventPublisher = eventPublisher;
        this.paymentCachingService = paymentCachingService;
    }

    private void scheduleRecurringPayment(String transactionId, Calendar nextRecurringDateTime, int attemptSequence) {
        try {
            paymentRenewalDao.save(PaymentRenewal.builder()
                    .day(nextRecurringDateTime)
                    .transactionId(transactionId)
                    .hour(nextRecurringDateTime.getTime())
                    .createdTimestamp(Calendar.getInstance())
                    .transactionEvent(PaymentEvent.SUBSCRIBE.name())
                    .attemptSequence(attemptSequence)
                    .build());
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY017, e);
        }
    }

    @Override
    public void schedulePaymentRenewal(Transaction transaction) {
        Calendar nextRecurringDateTime = Calendar.getInstance();
        nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, paymentCachingService.getPlan(transaction.getPlanId()).getPeriod().getValidity());
        scheduleRecurringPayment(transaction.getIdStr(), nextRecurringDateTime, transaction.getAttemptSequence());
    }

    @Override
    public void schedulePaymentRenewalForFreeTrial(Transaction transaction) {
        Calendar nextRecurringDateTime = Calendar.getInstance();
        nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, paymentCachingService.getPlan(paymentCachingService.getPlan(transaction.getPlanId()).getLinkedFreePlanId()).getPeriod().getValidity());
        scheduleRecurringPayment(transaction.getIdStr(), nextRecurringDateTime, transaction.getAttemptSequence());
    }

    @Override
    public void schedulePaymentRenewalForMigration(Transaction transaction) {
        scheduleRecurringPayment(transaction.getIdStr(), transaction.getValueFromPaymentMetaData(MIGRATED_NEXT_CHARGING_DATE), transaction.getAttemptSequence());
    }

    @Override
    public void schedulePaymentRenewalForNextRetry(Transaction transaction) {
        Calendar nextRecurringDateTime = Calendar.getInstance();
        PlanPeriodDTO planPeriodDTO = paymentCachingService.getPlan(transaction.getPlanId()).getPeriod();
        if (planPeriodDTO.getMaxRetryCount() < transaction.getAttemptSequence()) {
            return;
        }
        nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + planPeriodDTO.getTimeUnit().toSeconds(planPeriodDTO.getRetryInterval()));
        AnalyticService.update(planPeriodDTO);
        AnalyticService.update(nextRecurringDateTime);
        scheduleRecurringPayment(transaction.getIdStr(), nextRecurringDateTime, transaction.getAttemptSequence());
    }

    @Override
    @Transactional
    public Stream<PaymentRenewal> getCurrentDueRecurringPayments() {
        Calendar currentDay = Calendar.getInstance();
        Calendar currentDayWithOffset = Calendar.getInstance();
        Date currentTime = currentDay.getTime();
        Date currentTimeWithOffset = DateUtils.addHours(currentTime, dueRecurringOffsetTime);
        currentDayWithOffset.add(Calendar.DAY_OF_MONTH, dueRecurringOffsetDay);
        return paymentRenewalDao.getRecurrentPayment(currentDay, currentDayWithOffset, currentTime, currentTimeWithOffset);
    }

    @Override
    public void unScheduleRecurringPayment(String transactionId, PaymentEvent paymentEvent, long validTillDate) {
        try {
            paymentRenewalDao.findById(transactionId).ifPresent(recurringPayment -> {
                recurringPayment.setTransactionEvent(paymentEvent.name());
                Calendar calendar = Calendar.getInstance();
                recurringPayment.setUpdatedTimestamp(calendar);
                calendar.setTimeInMillis(validTillDate);
                recurringPayment.setDay(calendar);
                recurringPayment.setHour(calendar.getTime());
                paymentRenewalDao.save(recurringPayment);
                eventPublisher.publishEvent(RecurringPaymentEvent.builder()
                        .transactionId(transactionId)
                        .paymentEvent(PaymentEvent.UNSUBSCRIBE)
                        .build());
            });
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY017, e);
        }
    }

}

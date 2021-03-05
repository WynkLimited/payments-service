package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import lombok.extern.slf4j.Slf4j;
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
import static in.wynk.payment.core.constant.PaymentConstants.MESSAGE;

@Slf4j
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
    public void scheduleRecurringPayment(Transaction transaction, TransactionStatus existingTransactionStatus, TransactionStatus finalTransactionStatus) {
        if (existingTransactionStatus == TransactionStatus.INPROGRESS && finalTransactionStatus == TransactionStatus.MIGRATED && transaction.getType() == PaymentEvent.SUBSCRIBE) {
            scheduleRecurringPayment(transaction.getIdStr(), transaction.getValueFromPaymentMetaData(MIGRATED_NEXT_CHARGING_DATE), transaction.getAttemptSequence());
        } else {
            Calendar nextRecurringDateTime = Calendar.getInstance();
            PlanDTO planDTO = paymentCachingService.getPlan(transaction.getPlanId());
            if (existingTransactionStatus != TransactionStatus.SUCCESS && finalTransactionStatus == TransactionStatus.SUCCESS && transaction.getPaymentChannel().isInternalRecurring()) {
                if (transaction.getType() == PaymentEvent.SUBSCRIBE || transaction.getType() == PaymentEvent.RENEW) {
                    nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, planDTO.getPeriod().getValidity());
                    scheduleRecurringPayment(transaction.getIdStr(), nextRecurringDateTime, transaction.getAttemptSequence());
                } else if (transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
                    nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, paymentCachingService.getPlan(planDTO.getLinkedFreePlanId()).getPeriod().getValidity());
                    scheduleRecurringPayment(transaction.getIdStr(), nextRecurringDateTime, transaction.getAttemptSequence());
                }
            } else if (existingTransactionStatus == TransactionStatus.INPROGRESS && finalTransactionStatus == TransactionStatus.FAILURE
                    && (transaction.getType() == PaymentEvent.SUBSCRIBE || transaction.getType() == PaymentEvent.RENEW)
                    && transaction.getPaymentMetaData() != null && transaction.getPaymentMetaData().containsKey(PaymentConstants.RENEWAL)) {
                PlanPeriodDTO planPeriodDTO = planDTO.getPeriod();
                if (planPeriodDTO.getMaxRetryCount() < transaction.getAttemptSequence()) {
                    AnalyticService.update(MESSAGE, "Maximum Attempts Reached. No More Entry In Payment Renewal");
                    return;
                }
                nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + planPeriodDTO.getTimeUnit().toMillis(planPeriodDTO.getRetryInterval()));
                scheduleRecurringPayment(transaction.getIdStr(), nextRecurringDateTime, transaction.getAttemptSequence());
            }
        }
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
    public void unScheduleRecurringPayment(String transactionId, PaymentEvent paymentEvent, long validUntil, long deferredUntil) {
        try {
            paymentRenewalDao.findById(transactionId).ifPresent(recurringPayment -> {
                final Calendar hour = Calendar.getInstance();
                final Calendar day = recurringPayment.getDay();

                hour.setTime(recurringPayment.getHour());
                day.set(Calendar.SECOND, hour.get(Calendar.SECOND));
                day.set(Calendar.MINUTE, hour.get(Calendar.MINUTE));
                day.set(Calendar.HOUR_OF_DAY, hour.get(Calendar.HOUR_OF_DAY));

                final long deferredUntilNow = day.getTimeInMillis() - validUntil;
                final long furtherDeferUntil = deferredUntil - deferredUntilNow;

                if (furtherDeferUntil > 0) {
                    day.setTimeInMillis(recurringPayment.getDay().getTimeInMillis() + furtherDeferUntil);
                    hour.setTime(day.getTime());

                    recurringPayment.setDay(day);
                    recurringPayment.setHour(hour.getTime());
                    recurringPayment.setUpdatedTimestamp(Calendar.getInstance());
                    recurringPayment.setTransactionEvent(paymentEvent.name());

                    paymentRenewalDao.save(recurringPayment);
                    eventPublisher.publishEvent(RecurringPaymentEvent.builder().transactionId(transactionId).paymentEvent(paymentEvent).build());
                } else {
                    log.info("recurring can not be deferred further for transaction id {}, since offset {} is less than zero", transactionId, furtherDeferUntil);
                }
            });
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY017, e);
        }
    }

}

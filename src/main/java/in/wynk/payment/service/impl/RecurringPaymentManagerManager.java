package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.request.AbstractTransactionRevisionRequest;
import in.wynk.payment.dto.request.MigrationTransactionRevisionRequest;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.stream.Stream;

import static in.wynk.payment.core.constant.PaymentConstants.MESSAGE;

@Slf4j
@Service(BeanConstant.RECURRING_PAYMENT_RENEWAL_SERVICE)
public class RecurringPaymentManagerManager implements IRecurringPaymentManagerService {

    private final ISqsManagerService sqsManagerService;
    private final IPaymentRenewalDao paymentRenewalDao;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCachingService paymentCachingService;
    @Value("${payment.recurring.offset.day}")
    private int dueRecurringOffsetDay;
    @Value("${payment.recurring.offset.hour}")
    private int dueRecurringOffsetTime;
    @Value("${payment.preDebitNotification.preOffsetDays}")
    private int preDebitNotificationPreOffsetDay;
    @Value("${payment.preDebitNotification.offset.day}")
    private int duePreDebitNotificationOffsetDay;
    @Value("${payment.preDebitNotification.offset.hour}")
    private int duePreDebitNotificationOffsetTime;

    public RecurringPaymentManagerManager(ISqsManagerService sqsManagerService,
                                          @Qualifier(BeanConstant.PAYMENT_RENEWAL_DAO) IPaymentRenewalDao paymentRenewalDao,
                                          ApplicationEventPublisher eventPublisher,
                                          PaymentCachingService paymentCachingService) {
        this.sqsManagerService = sqsManagerService;
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
    public void scheduleRecurringPayment(AbstractTransactionRevisionRequest request) {
        if (MigrationTransactionRevisionRequest.class.isAssignableFrom(request.getClass()) && request.getExistingTransactionStatus() == TransactionStatus.INPROGRESS && request.getFinalTransactionStatus() == TransactionStatus.MIGRATED && request.getTransaction().getType() == PaymentEvent.SUBSCRIBE) {
            scheduleRecurringPayment(request.getTransaction().getIdStr(), ((MigrationTransactionRevisionRequest) request).getNextChargingDate(), 0);
        } else {
            Calendar nextRecurringDateTime = Calendar.getInstance();
            PlanDTO planDTO = paymentCachingService.getPlan(request.getTransaction().getPlanId());
            if (request.getExistingTransactionStatus() != TransactionStatus.SUCCESS && request.getFinalTransactionStatus() == TransactionStatus.SUCCESS && request.getTransaction().getPaymentChannel().isInternalRecurring()) {
                if (EnumSet.of(PaymentEvent.SUBSCRIBE, PaymentEvent.RENEW).contains(request.getTransaction().getType())) {
                    nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + planDTO.getPeriod().getTimeUnit().toMillis(planDTO.getPeriod().getValidity()));
                    scheduleRecurringPayment(request.getTransaction().getIdStr(), nextRecurringDateTime, request.getAttemptSequence());
                } else if (request.getTransaction().getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
                    nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + planDTO.getPeriod().getTimeUnit().toMillis(planDTO.getPeriod().getValidity()));
                    scheduleRecurringPayment(request.getTransaction().getIdStr(), nextRecurringDateTime, request.getAttemptSequence());
                }
            } else if (request.getExistingTransactionStatus() == TransactionStatus.INPROGRESS && request.getFinalTransactionStatus() == TransactionStatus.FAILURE && request.getTransaction().getType() == PaymentEvent.RENEW && request.getTransaction().getPaymentChannel().isInternalRecurring()) {
                PlanPeriodDTO planPeriodDTO = planDTO.getPeriod();
                if (planPeriodDTO.getMaxRetryCount() < request.getAttemptSequence()) {
                    AnalyticService.update(MESSAGE, "Maximum Attempts Reached. No More Entry In Payment Renewal");
                    return;
                }
                nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + planPeriodDTO.getTimeUnit().toMillis(planPeriodDTO.getRetryInterval()) + 1 * 60 * 60 * 1000); // 1 hour more than proper date
                scheduleRecurringPayment(request.getTransactionId(), nextRecurringDateTime, request.getAttemptSequence());
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                sqsManagerService.publishSQSMessage(PreDebitNotificationMessage.builder().date(format.format(nextRecurringDateTime.getTime())).transactionId(request.getTransactionId()).build());
            }
        }
    }

    @Override
    @Transactional
    public Stream<PaymentRenewal> getCurrentDueNotifications() {
        return getPaymentRenewalStream(duePreDebitNotificationOffsetDay, duePreDebitNotificationOffsetTime, preDebitNotificationPreOffsetDay);
    }

    @Override
    @Transactional
    public Stream<PaymentRenewal> getCurrentDueRecurringPayments() {
        return getPaymentRenewalStream(dueRecurringOffsetDay, dueRecurringOffsetTime, 0);
    }

    private Stream<PaymentRenewal> getPaymentRenewalStream(int offsetDay, int offsetTime, int preOffsetDays) {
        final Calendar currentDay = Calendar.getInstance();
        currentDay.add(Calendar.DAY_OF_MONTH, preOffsetDays);
        final Calendar currentDayTimeWithOffset = Calendar.getInstance();
        currentDayTimeWithOffset.add(Calendar.DAY_OF_MONTH, offsetDay + preOffsetDays);
        currentDayTimeWithOffset.add(Calendar.HOUR_OF_DAY, offsetTime);
        final Date currentTime = currentDay.getTime();
        final Date currentTimeWithOffset = currentDayTimeWithOffset.getTime();
        if (currentDay.get(Calendar.DAY_OF_MONTH) != currentDayTimeWithOffset.get(Calendar.DAY_OF_MONTH)) {
            currentDay.set(Calendar.HOUR_OF_DAY, 23);
            currentDay.set(Calendar.MINUTE, 59);
            currentDay.set(Calendar.SECOND, 59);
            currentDay.set(Calendar.MILLISECOND, 999);
            currentDayTimeWithOffset.set(Calendar.HOUR_OF_DAY, 00);
            currentDayTimeWithOffset.set(Calendar.MINUTE, 00);
            currentDayTimeWithOffset.set(Calendar.SECOND, 00);
            currentDayTimeWithOffset.set(Calendar.MILLISECOND, 999);
            final Date[] lowerRangeBound = new Date[]{currentTime, currentDayTimeWithOffset.getTime()};
            final Date[] upperRangeBound = new Date[]{currentDay.getTime(), currentTimeWithOffset};
            return Stream.concat(paymentRenewalDao.getRecurrentPayment(currentDay, currentDay, lowerRangeBound[0], upperRangeBound[0]), paymentRenewalDao.getRecurrentPayment(currentDayTimeWithOffset, currentDayTimeWithOffset, lowerRangeBound[1], upperRangeBound[1]));
        }
        return paymentRenewalDao.getRecurrentPayment(currentDay, currentDayTimeWithOffset, currentTime, currentTimeWithOffset);
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
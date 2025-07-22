package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.mchange.v2.cfg.PropertiesConfigSource;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.aspect.advice.Transactional;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.core.event.UnScheduleRecurringPaymentEvent;
import in.wynk.payment.dto.SubscriptionStatus;
import in.wynk.payment.dto.addtobill.AddToBillUserSubscriptionStatusTask;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.request.AbstractTransactionRevisionRequest;
import in.wynk.payment.dto.request.MigrationTransactionRevisionRequest;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.scheduler.task.dto.TaskDefinition;
import in.wynk.scheduler.task.service.ITaskScheduler;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.SimpleScheduleBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static in.wynk.payment.core.constant.PaymentConstants.MESSAGE;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Slf4j
@RequiredArgsConstructor
@Service(BeanConstant.RECURRING_PAYMENT_RENEWAL_SERVICE)
public class RecurringPaymentManager implements IRecurringPaymentManagerService {

    private final ApplicationEventPublisher eventPublisher;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final ITaskScheduler<TaskDefinition<?>> taskScheduler;
    @Value("${payment.recurring.offset.day}")
    private int dueRecurringOffsetDay;
    @Value("${payment.recurring.offset.hour}")
    private int dueRecurringOffsetTime;
    @Value("${payment.recurring.retry.hour}")
    private int dueRecurringRetryTime;
    @Value("${payment.preDebitNotification.preOffsetDays}")
    private int preDebitNotificationPreOffsetDay;
    @Value("${payment.preDebitNotification.offset.day}")
    private int duePreDebitNotificationOffsetDay;
    @Value("${payment.preDebitNotification.offset.hour}")
    private int duePreDebitNotificationOffsetTime;
    @Value("${payment.recurring.peak.morning}")
    private String morningPeakHours;
    @Value("${payment.recurring.peak.evening}")
    private String eveningPeakHours;
    @Value("${payment.npciRenewalWindowUpdate.preOffsetDays}")
    private int npciRenewalWindowUpdatePreOffsetDay;
    @Value("${payment.npciRenewalWindowUpdate.offset.day}")
    private int npciRenewalWindowUpdateOffsetDay;
    @Value("${payment.npciRenewalWindowUpdate.offset.hour}")
    private int npciRenewalWindowUpdateOffsetTime;


    private final Map<String, Integer> CODE_TO_RENEW_OFFSET = new HashMap<String, Integer>() {{
        put(BeanConstant.AIRTEL_PAY_STACK, -2);
    }};

    @Override
    public void scheduleRecurringPayment (AbstractTransactionRevisionRequest request) {
        if (MigrationTransactionRevisionRequest.class.isAssignableFrom(request.getClass()) && request.getExistingTransactionStatus() == TransactionStatus.INPROGRESS &&
                request.getFinalTransactionStatus() == TransactionStatus.MIGRATED && request.getTransaction().getType() == PaymentEvent.SUBSCRIBE) {
            updateRenewalEntry(request.getTransaction().getIdStr(), request.getLastSuccessTransactionId(), request.getTransaction().getType(),
                    request.getTransaction().getPaymentChannel().getCode(), ((MigrationTransactionRevisionRequest) request).getNextChargingDate(), request.getTransaction(),
                    request.getFinalTransactionStatus(), null);
        } else {
            Calendar nextRecurringDateTime = Calendar.getInstance();
            Integer planId = subscriptionServiceManager.getUpdatedPlanId(request.getTransaction().getPlanId(), request.getTransaction().getType());
            PlanDTO planDTO = BeanLocatorFactory.getBean(PaymentCachingService.class).getPlan(planId);
            PaymentRenewal renewal= getRenewalById(request.getTransaction().getIdStr());
            if (request.getExistingTransactionStatus() != TransactionStatus.SUCCESS && request.getFinalTransactionStatus() == TransactionStatus.SUCCESS &&
                    request.getTransaction().getPaymentChannel().isInternalRecurring()) {
                if (EnumSet.of(PaymentEvent.SUBSCRIBE, PaymentEvent.TRIAL_SUBSCRIPTION).contains(request.getTransaction().getType())) {
                    nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + planDTO.getPeriod().getTimeUnit().toMillis(planDTO.getPeriod().getValidity()));
                    createRenewalEntry(request.getTransaction().getIdStr(), request.getTransaction().getType(), nextRecurringDateTime, request.getTransaction(), request.getFinalTransactionStatus(), request.getTransaction().getPaymentChannel().getCode());

                } else if (request.getTransaction().getType() == PaymentEvent.RENEW) {
                    nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + planDTO.getPeriod().getTimeUnit().toMillis(planDTO.getPeriod().getValidity()));
                    updateRenewalEntry(request.getTransaction().getIdStr(), request.getLastSuccessTransactionId(), request.getTransaction().getType(),
                            request.getTransaction().getPaymentChannel().getCode(), nextRecurringDateTime, request.getTransaction(), request.getFinalTransactionStatus(),
                            renewal);
                } else if (request.getTransaction().getType() == PaymentEvent.MANDATE) {
                    setRenewalDate(request, nextRecurringDateTime, planDTO);
                }
            } else if (request.getExistingTransactionStatus() == TransactionStatus.INPROGRESS && (request.getFinalTransactionStatus() == TransactionStatus.FAILURE || request.getFinalTransactionStatus() == TransactionStatus.FAILUREALREADYSUBSCRIBED) &&
                    request.getTransaction().getType() == PaymentEvent.RENEW && request.getTransaction().getPaymentChannel().isInternalRecurring()) {

                if (Objects.nonNull(renewal) && renewal.getTransactionEvent() == PaymentEvent.CANCELLED) {
                    return;
                } else if ((Objects.nonNull(renewal) && (renewal.getAttemptSequence() >= PaymentConstants.MAXIMUM_RENEWAL_RETRY_ALLOWED)) ||
                        (request.getAttemptSequence() >= PaymentConstants.MAXIMUM_RENEWAL_RETRY_ALLOWED)) {
                    AnalyticService.update(MESSAGE, "Maximum Attempts Reached. No More Entry In Payment Renewal");
                    eventPublisher.publishEvent(UnScheduleRecurringPaymentEvent.builder().transactionId(request.getTransaction().getIdStr()).clientAlias(request.getTransaction().getClientAlias())
                            .reason("Maximum Attempts Reached. No More Entry In Payment Renewal").build());
                    return;
                }
                PlanPeriodDTO planPeriodDTO = planDTO.getPeriod();
                nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + planPeriodDTO.getTimeUnit().toMillis(planPeriodDTO.getRetryInterval()));
                updateRenewalEntry(request.getTransaction().getIdStr(), request.getLastSuccessTransactionId(), request.getTransaction().getType(),
                        request.getTransaction().getPaymentChannel().getCode(), nextRecurringDateTime, request.getTransaction(), request.getFinalTransactionStatus(),
                        renewal);
            }
        }
    }

    private void createRenewalEntry(String transactionId, PaymentEvent event, Calendar nextRecurringDateTime, Transaction transaction, TransactionStatus finalTransactionStatus, String paymentCode){
        if (BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE.equalsIgnoreCase(paymentCode)) {
            if (finalTransactionStatus != TransactionStatus.FAILURE) {
                scheduleAtbTask(transaction, nextRecurringDateTime);
            }
            return;
        }
        if (CODE_TO_RENEW_OFFSET.containsKey(paymentCode)) {
            final Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_MONTH, 3);
            if (nextRecurringDateTime.compareTo(day) >= 0) {
                nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, CODE_TO_RENEW_OFFSET.get(paymentCode));
            }
        }
        scheduleToNonPeakHours(nextRecurringDateTime);
        PaymentRenewal paymentRenewal = PaymentRenewal.builder().day(nextRecurringDateTime).transactionId(transactionId).hour(nextRecurringDateTime.getTime()).createdTimestamp(Calendar.getInstance())
                .transactionEvent(String.valueOf(event))
                .initialTransactionId(transactionId).lastSuccessTransactionId(null)
                .attemptSequence(0).build();
        upsert(paymentRenewal);

    }

    private void setRenewalDate (AbstractTransactionRevisionRequest request, Calendar nextRecurringDateTime, PlanDTO planDTO) {
        PaymentRenewal renewal= getRenewalById(request.getTransaction().getIdStr());
        try {
            Optional<SubscriptionStatus> subscriptionStatusOptional = subscriptionServiceManager.getSubscriptionStatus(request.getTransaction().getUid(), planDTO.getService()).stream()
                    .filter(status -> status.getPlanId() == request.getTransaction().getPlanId()).findAny();
            if (subscriptionStatusOptional.isPresent()) {
                nextRecurringDateTime.setTimeInMillis(subscriptionStatusOptional.get().getValidity());
                updateRenewalEntry(request.getTransaction().getIdStr(), request.getLastSuccessTransactionId(), request.getTransaction().getType(),
                        request.getTransaction().getPaymentChannel().getCode(), nextRecurringDateTime, request.getTransaction(), request.getFinalTransactionStatus(),
                        renewal);
                return;
            }
            throw new WynkRuntimeException("No end date found from subscription. So, setting default time for renewal for plan id " + planDTO.getId());
        } catch (Exception e) {
            nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + ((long) dueRecurringRetryTime * 60 * 60 * 1000));
            updateRenewalEntry(request.getTransaction().getIdStr(), request.getLastSuccessTransactionId(), request.getTransaction().getType(),
                    request.getTransaction().getPaymentChannel().getCode(), nextRecurringDateTime, request.getTransaction(), request.getFinalTransactionStatus(), renewal);
        }
    }

    @Override
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void unScheduleRecurringPayment (String clientAlias, String transactionId, PaymentEvent paymentEvent) {
        Calendar nextRecurringDateTime = Calendar.getInstance();
        final IPaymentRenewalDao paymentRenewalDao = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class);
        paymentRenewalDao.findById(transactionId).ifPresent(recurringPayment -> {
            recurringPayment.setTransactionEvent(paymentEvent.name());
            recurringPayment.setUpdatedTimestamp(nextRecurringDateTime);
            recurringPayment.setDay(nextRecurringDateTime);
            paymentRenewalDao.save(recurringPayment);
        });
    }

    @Override
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public Stream<PaymentRenewal> getCurrentDueNotifications (String clientAlias) {
        return getPaymentRenewalStream(duePreDebitNotificationOffsetDay, duePreDebitNotificationOffsetTime, preDebitNotificationPreOffsetDay);
    }

    @Override
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public Stream<PaymentRenewal> getCurrentDueRecurringPayments (String clientAlias) {
        return getPaymentRenewalStream(dueRecurringOffsetDay, dueRecurringOffsetTime, 0);
    }

    @Override
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public Stream<PaymentRenewal> getNextDayRecurringPayments(String clientAlias) {
        return getPaymentRenewalStream(npciRenewalWindowUpdateOffsetDay, npciRenewalWindowUpdateOffsetTime, npciRenewalWindowUpdatePreOffsetDay);
    }

    @Override
    public PaymentRenewal getLatestRecurringPaymentByInitialTxnId (String txnId) {
        Optional<PaymentRenewal> paymentRenewalOptional = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class).findTopByInitialTransactionIdOrderByCreatedTimestampDesc(txnId);
        return paymentRenewalOptional.orElse(null);
    }

    private Stream<PaymentRenewal> getPaymentRenewalStream (int offsetDay, int offsetTime, int preOffsetDays) {
        final Calendar currentDay = Calendar.getInstance();
        currentDay.add(Calendar.DAY_OF_MONTH, preOffsetDays);
        final Calendar currentDayTimeWithOffset = Calendar.getInstance();
        currentDayTimeWithOffset.add(Calendar.DAY_OF_MONTH, offsetDay + preOffsetDays);
        currentDayTimeWithOffset.add(Calendar.HOUR_OF_DAY, offsetTime);
        final Date currentTime = currentDay.getTime();
        final Date currentTimeWithOffset = currentDayTimeWithOffset.getTime();
        final IPaymentRenewalDao paymentRenewalDao = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class);
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
            return Stream.concat(paymentRenewalDao.getRecurrentPayment(currentDay, currentDay, lowerRangeBound[0], upperRangeBound[0]),
                    paymentRenewalDao.getRecurrentPayment(currentDayTimeWithOffset, currentDayTimeWithOffset, lowerRangeBound[1], upperRangeBound[1]));
        }
        return paymentRenewalDao.getRecurrentPayment(currentDay, currentDayTimeWithOffset, currentTime, currentTimeWithOffset);
    }

    private Set<Integer> parseHours(String hoursStr) {
        return Arrays.stream(hoursStr.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
    }

    @Override
    public void scheduleToNonPeakHours(Calendar calendar) {
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        Set<Integer> morningPeak = parseHours(morningPeakHours);
        Set<Integer> eveningPeak = parseHours(eveningPeakHours);

        if (morningPeak.contains(hour)) {
            calendar.set(Calendar.HOUR_OF_DAY, new Random().nextInt(4) + 6);

        } else if (eveningPeak.contains(hour)) {
            calendar.set(Calendar.HOUR_OF_DAY, new Random().nextInt(4) + 13);
        } else {
            return;
        }

//        calendar.set(Calendar.MINUTE, 0);
//        calendar.set(Calendar.SECOND, 0);
//        calendar.set(Calendar.MILLISECOND, 0);
    }


    @Override
    public void updateRenewalEntry (String transactionId, String originalTransactionId, PaymentEvent paymentEvent, String code, Calendar nextRecurringDateTime,
                                          Transaction transaction, TransactionStatus finalTransactionStatus, PaymentRenewal renewal) {
        //remove this ATB code in future
        if (BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE.equalsIgnoreCase(code)) {
            if (finalTransactionStatus != TransactionStatus.FAILURE) {
                scheduleAtbTask(transaction, nextRecurringDateTime);
            }
            return;
        }
        if (CODE_TO_RENEW_OFFSET.containsKey(code)) {
            final Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_MONTH, 3);
            if (nextRecurringDateTime.compareTo(day) >= 0) {
                nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, CODE_TO_RENEW_OFFSET.get(code));
            }
        }
        scheduleToNonPeakHours(nextRecurringDateTime);

        String merchantTransactionEvent= renewal.getTransactionEvent().name();
        if (finalTransactionStatus== TransactionStatus.SUCCESS){
            merchantTransactionEvent= PaymentEvent.SUBSCRIBE.name();
        }
        else if(finalTransactionStatus == TransactionStatus.FAILURE){
            merchantTransactionEvent= PaymentEvent.DEFERRED.name();
        }

        PaymentRenewal paymentRenewal = PaymentRenewal.builder().day(nextRecurringDateTime).transactionId(transactionId).hour(nextRecurringDateTime.getTime()).createdTimestamp(Calendar.getInstance())
                .transactionEvent(merchantTransactionEvent)
                .initialTransactionId(renewal.getInitialTransactionId()).lastSuccessTransactionId(renewal.getLastSuccessTransactionId())
                .attemptSequence(renewal.getAttemptSequence()).build();
        upsert(paymentRenewal);
    }

    @Override
    public void createEntryInRenewalTable(String transactionId, String previousTransactionId, PaymentEvent event, String paymentCode, Calendar nextRecurringDateTime, int attemptSequence, Transaction transaction, TransactionStatus finalTransactionStatus, PaymentRenewal previousRenewal, Transaction previousTransaction) {
        //remove this ATB piece of code after testing
        if (BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE.equalsIgnoreCase(paymentCode)) {
            if (finalTransactionStatus != TransactionStatus.FAILURE) {
                scheduleAtbTask(transaction, nextRecurringDateTime);
            }
            return;
        }
        if (CODE_TO_RENEW_OFFSET.containsKey(paymentCode)) {
            final Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_MONTH, 3);
            if (nextRecurringDateTime.compareTo(day) >= 0) {
                nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, CODE_TO_RENEW_OFFSET.get(paymentCode));
            }
        }

        scheduleToNonPeakHours(nextRecurringDateTime);

        int updatedAttemptSequence= attemptSequence;
        TransactionStatus previousStatus= previousTransaction.getStatus();
        if(previousStatus== TransactionStatus.SUCCESS || previousStatus== TransactionStatus.INPROGRESS){
            updatedAttemptSequence=0;
        }
        else if (previousStatus== TransactionStatus.FAILURE || previousStatus== TransactionStatus.FAILUREALREADYSUBSCRIBED){
            updatedAttemptSequence= attemptSequence+1;
        }
        String lastSuccessTransactionId= previousTransaction.getStatus() == TransactionStatus.SUCCESS ? previousTransactionId : previousRenewal.getLastSuccessTransactionId();
        String initialTransactionId= previousTransaction.getType()== PaymentEvent.SUBSCRIBE ? previousTransactionId : previousRenewal.getInitialTransactionId();
        PaymentRenewal paymentRenewal= PaymentRenewal.builder().day(nextRecurringDateTime).transactionId(transactionId).hour(nextRecurringDateTime.getTime()).createdTimestamp(Calendar.getInstance())
                .transactionEvent(PaymentEvent.SUBSCRIBE.name()).initialTransactionId(initialTransactionId)
                .lastSuccessTransactionId(lastSuccessTransactionId).attemptSequence(updatedAttemptSequence)
                .build();
        upsert(paymentRenewal);
    }

    private String fetchInitialTransactionId (Transaction transaction, PaymentEvent event, String updatedLastSuccessTransactionId) {
        if (PaymentEvent.RENEW != event) {
            return transaction.getIdStr();
        } else {
            if (updatedLastSuccessTransactionId != null) {
                PaymentRenewal renewal = getRenewalById(updatedLastSuccessTransactionId);
                return renewal != null ? renewal.getInitialTransactionId() : null;
            } else {
                return null;
            }
        }
    }

    @Override
    public void scheduleAtbTask (Transaction transaction, Calendar nextRecurringDateTime) {
        try {
            taskScheduler.schedule(TaskDefinition.builder()
                    .handler(ATBUserSubscriptionStatusHandler.class)
                    .entity(AddToBillUserSubscriptionStatusTask.builder()
                            .transactionId(transaction.getIdStr())
                            .paymentCode(String.valueOf(transaction.getPaymentChannel().getCode()))
                            .si(transaction.getMsisdn().replace("+91", ""))
                            .build())
                    .triggerConfiguration(TaskDefinition.TriggerConfiguration.builder()
                            .durable(false)
                            .startAt(nextRecurringDateTime.getTime())
                            .scheduleBuilder(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(0).withRepeatCount(0))
                            .build())
                    .build());
        } catch (Exception e) {
            log.error("Unable to schedule addtoBill Task for checking mandate status of ATB on renewal date: {}", e.getMessage());
        }
    }

    @Override
    public void unScheduleRecurringPayment (Transaction transaction, PaymentEvent paymentEvent, long validUntil, long deferredUntil) {
        String transactionId = transaction.getIdStr();
        try {
            final IPaymentRenewalDao paymentRenewalDao = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class);
            paymentRenewalDao.findById(transactionId).ifPresent(recurringPayment -> {
                boolean publishDeferredEvent = true;
                if (PaymentEvent.UNSUBSCRIBE == paymentEvent) {
                    recurringPayment.setTransactionEvent(paymentEvent.name());
                } else {
                    final Calendar hour = Calendar.getInstance();
                    final Calendar day = recurringPayment.getDay();
                    hour.setTime(recurringPayment.getHour());
                    day.set(Calendar.SECOND, hour.get(Calendar.SECOND));
                    day.set(Calendar.MINUTE, hour.get(Calendar.MINUTE));
                    day.set(Calendar.HOUR_OF_DAY, hour.get(Calendar.HOUR_OF_DAY));
                    if (PaymentEvent.DEFERRED == paymentEvent) {
                        final long deferredUntilNow = day.getTimeInMillis() - validUntil;
                        final long furtherDeferUntil = deferredUntil - deferredUntilNow;
                        long maxIgnoreEventDuration = Calendar.getInstance().getTimeInMillis() + ((long) 3 * 24 * 60 * 60 * 1000);
                        long deferredTime = recurringPayment.getDay().getTimeInMillis() + furtherDeferUntil;
                        if (deferredTime < maxIgnoreEventDuration) {
                            publishDeferredEvent = false;
                        }
                        if (furtherDeferUntil > 0) {
                            day.setTimeInMillis(recurringPayment.getDay().getTimeInMillis() + furtherDeferUntil);
                        } else {
                            log.info("recurring can not be deferred further for transaction id {}, since offset {} is less than zero", transactionId, furtherDeferUntil);
                            return;
                        }
                    } else if (PaymentEvent.CANCELLED == paymentEvent) {
                        day.setTimeInMillis(recurringPayment.getDay().getTimeInMillis());
                    }
                    hour.setTime(day.getTime());
                    recurringPayment.setDay(day);
                    recurringPayment.setHour(hour.getTime());
                    recurringPayment.setUpdatedTimestamp(Calendar.getInstance());
                    recurringPayment.setTransactionEvent(paymentEvent.name());
                }
                paymentRenewalDao.save(recurringPayment);
                // if renewal dare is more than 3 days then only publish deferred eventif
                if (publishDeferredEvent) {
                    eventPublisher.publishEvent(RecurringPaymentEvent.builder().transaction(transaction).paymentEvent(paymentEvent).build());
                }
            });
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY099, e);
        }
    }

    @Override
    public void upsert (PaymentRenewal paymentRenewal) {
        log.info("Upserting into payment renewal table for txnId {}", paymentRenewal.getTransactionId());
        try {
            RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class).save(paymentRenewal);
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY017, e);
        }
    }

    @Override
    public PaymentRenewal getRenewalById (String txnId) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class).findById(txnId)
                .orElse(null);
    }

    @Override
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void updateRenewalSchedule (String clientAlias, String transactionId, Calendar day, Date hour) {
            final IPaymentRenewalDao paymentRenewalDao = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class);
            paymentRenewalDao.findById(transactionId).ifPresent(recurringPayment -> {
                recurringPayment.setDay(day);
                recurringPayment.setHour(hour);
            });
    }
}
package in.wynk.payment.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.dao.entity.Coupon;
import in.wynk.coupon.core.dao.entity.CouponCodeLink;
import in.wynk.coupon.core.service.CouponCachingService;
import in.wynk.coupon.core.service.ICouponCodeLinkService;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.*;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.request.AsyncTransactionRevisionRequest;
import in.wynk.payment.dto.request.ClientCallbackRequest;
import in.wynk.payment.dto.request.PaymentSettlementRequest;
import in.wynk.payment.dto.response.AbstractPaymentSettlementResponse;
import in.wynk.payment.handler.CustomerWinBackHandler;
import in.wynk.payment.service.*;
import in.wynk.queue.constant.QueueConstant;
import in.wynk.queue.dto.MessageThresholdExceedEvent;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.scheduler.task.dto.TaskDefinition;
import in.wynk.scheduler.task.service.ITaskScheduler;
import in.wynk.stream.producer.IKinesisEventPublisher;
import in.wynk.tinylytics.dto.BranchEvent;
import in.wynk.tinylytics.dto.BranchRawDataEvent;
import in.wynk.tinylytics.utils.AppUtils;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SimpleScheduleBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.exception.WynkErrorType.UT025;
import static in.wynk.exception.WynkErrorType.UT999;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_CODE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.queue.constant.BeanConstant.MESSAGE_PAYLOAD;
import static in.wynk.tinylytics.constants.TinylyticsConstants.EVENT;
import static in.wynk.tinylytics.constants.TinylyticsConstants.TRANSACTION_SNAPShOT_EVENT;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventListener {
    private final ObjectMapper mapper;
    private final RetryRegistry retryRegistry;
    private final PaymentManager paymentManager;
    private final ITaskScheduler taskScheduler;
    private final ISqsManagerService sqsManagerService;
    private final IPaymentErrorService paymentErrorService;
    private final ApplicationEventPublisher eventPublisher;
    private final IClientCallbackService clientCallbackService;
    private final ITransactionManagerService transactionManagerService;
    private final IMerchantTransactionService merchantTransactionService;
    @Value("${event.stream.dp}")
    private String dpStream;


    @EventListener
    @AnalyseTransaction(name = QueueConstant.DEFAULT_SQS_MESSAGE_THRESHOLD_EXCEED_EVENT)
    public void onAnyOrderMessageThresholdExceedEvent(MessageThresholdExceedEvent event) throws JsonProcessingException {
        AnalyticService.update(event);
        AnalyticService.update(MESSAGE_PAYLOAD, mapper.writeValueAsString(event));
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "paymentReconciliationThresholdExceedEvent")
    public void onPaymentReconThresholdExceedEvent(PaymentReconciliationThresholdExceedEvent event) {
        AnalyticService.update(event);
        final Transaction transaction = transactionManagerService.get(event.getTransactionId());
        final TransactionStatus existingTransactionStatus = transaction.getStatus();
        transaction.setStatus(TransactionStatus.TIMEDOUT.getValue());
        transactionManagerService.revision(
                AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingTransactionStatus).finalTransactionStatus(transaction.getStatus()).build());
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "paymentRenewalMessageThresholdExceedEvent")
    public void onPaymentRenewalMessageThresholdExceedEvent(PaymentRenewalMessageThresholdExceedEvent event) {
        AnalyticService.update(event);
        Transaction transaction = transactionManagerService.get(event.getTransactionId());
        sqsManagerService.publishSQSMessage(PaymentRenewalChargingMessage.builder()
                .uid(transaction.getUid())
                .id(transaction.getIdStr())
                .planId(transaction.getPlanId())
                .msisdn(transaction.getMsisdn())
                .clientAlias(transaction.getClientAlias())
                .attemptSequence(event.getAttemptSequence())
                .paymentCode(transaction.getPaymentChannel().getId())
                .build());
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "recurringPaymentEvent")
    public void onRecurringPaymentEvent(RecurringPaymentEvent event) {
        try {
            AnalyticService.update(event);
            if (event.getPaymentEvent() == PaymentEvent.UNSUBSCRIBE)
                BeanLocatorFactory.getBean(transactionManagerService.get(event.getTransactionId()).getPaymentChannel().getCode(), ICancellingRecurringService.class)
                        .cancelRecurring(event.getTransactionId());
        } catch (Exception e) {
            throw new WynkRuntimeException(UT025, e);
        }
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "merchantTransactionEvent")
    public void onMerchantTransactionEvent(MerchantTransactionEvent event) {
        AnalyticService.update(event);
        retryRegistry.retry(PaymentConstants.MERCHANT_TRANSACTION_UPSERT_RETRY_KEY).executeRunnable(() -> merchantTransactionService.upsert(MerchantTransaction.builder()
                .id(event.getId())
                .externalTransactionId(event.getExternalTransactionId())
                .request(event.getRequest())
                .response(event.getResponse())
                .build()
        ));
    }

    @EventListener
    @AnalyseTransaction(name = "paymentErrorEvent")
    @ClientAware(clientAlias = "#event.clientAlias")
    public void onPaymentErrorEvent(PaymentErrorEvent event) {
        AnalyticService.update(event);
        retryRegistry.retry(PaymentConstants.PAYMENT_ERROR_UPSERT_RETRY_KEY).executeRunnable(() -> paymentErrorService.upsert(PaymentError.builder()
                .id(event.getId())
                .code(event.getCode())
                .description(event.getDescription())
                .build()));
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "paymentRefundInitEvent")
    public void onPaymentRefundInitEvent(PaymentRefundInitEvent event) {
        AnalyticService.update(event);
        WynkResponseEntity<?> response = paymentManager.refund(PaymentRefundInitRequest.builder().originalTransactionId(event.getOriginalTransactionId()).reason(event.getReason()).build());
        AnalyticService.update(response.getBody());
    }

    @EventListener
    @AnalyseTransaction(name = "paymentRefundedEvent")
    public void onPaymentRefundEvent(PaymentRefundReconciledEvent event) {
        AnalyticService.update(event);
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "paymentReconciledEvent")
    public void onPaymentReconciledEvent(PaymentChargingReconciledEvent event) {
        AnalyticService.update(event);
        initRefundIfApplicable(event);
    }

    @EventListener
    @AnalyseTransaction(name = "clientCallback")
    @ClientAware(clientAlias = "#event.clientAlias")
    public void onClientCallbackEvent(ClientCallbackEvent callbackEvent) {
        AnalyticService.update(callbackEvent);
        sendClientCallback(callbackEvent.getClientAlias(), ClientCallbackRequest.from(callbackEvent));
    }


    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    public void onPurchaseInit(PurchaseInitEvent purchaseInitEvent) {
        dropOutTracker(PurchaseRecord.from(purchaseInitEvent));
    }

    private void sendClientCallback(String clientAlias, ClientCallbackRequest request) {
        clientCallbackService.sendCallback(ClientCallbackPayloadWrapper.<ClientCallbackRequest>builder().clientAlias(clientAlias).payload(request).build());
    }

    private void initRefundIfApplicable(PaymentChargingReconciledEvent event) {
        if (EnumSet.of(TransactionStatus.SUCCESS).contains(event.getTransactionStatus()) && EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION).contains(event.getPaymentEvent()) &&
                event.getPaymentGateway().isTrialRefundSupported()) {
            eventPublisher.publishEvent(PaymentRefundInitEvent.builder()
                    .reason("trial plan amount refund")
                    .originalTransactionId(event.getTransactionId())
                    .build());
        }
    }

    @ClientAware(clientAlias = "#purchaseRecord.clientAlias")
    private void dropOutTracker(PurchaseRecord purchaseRecord) {
        try {
            final ClientDetails clientDetails = (ClientDetails) ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
            if (taskScheduler.isTriggerExist(purchaseRecord.getGroupId(), purchaseRecord.getTaskId())) {
                taskScheduler.unSchedule(purchaseRecord.getGroupId(), purchaseRecord.getTaskId());
            }
            final long delayedBy = clientDetails.<Long>getMeta(PaymentConstants.PAYMENT_DROPOUT_DELAY_KEY).orElse(3600L);
            taskScheduler.schedule(TaskDefinition.<PurchaseRecord>builder().entity(purchaseRecord).handler(CustomerWinBackHandler.class).triggerConfiguration(
                    TaskDefinition.TriggerConfiguration.builder().startAt(new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delayedBy)))
                            .scheduleBuilder(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0).withIntervalInSeconds(0)).build()).build());
        } catch (Exception e) {
            log.error(WynkErrorType.UT999.getMarker(), "something went wrong while scheduling task due to {}", e.getMessage(), e);
        }
    }

    @EventListener
    @AnalyseTransaction(name = "paymentSettlement")
    public void onPaymentSettlementEvent(PaymentSettlementEvent event) {
        final AbstractPaymentSettlementResponse response = paymentManager.settle(PaymentSettlementRequest.builder().tid(event.getTid()).build());
        AnalyticService.update(event);
        AnalyticService.update(response);
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    public void onPaymentUserDeactivationEvent(PaymentUserDeactivationEvent event) {
        try {
            transactionManagerService.migrateOldTransactions(event.getId(), event.getUid(), event.getOldUid(), event.getService());
        } catch (Exception e) {
            throw new WynkRuntimeException(UT999, e);
        }
    }

    @EventListener
    @AnalyseTransaction(name = "paymentUserDeactivationMigrationEvent")
    public void onPaymentUserDeactivationMigrationEvent(PaymentUserDeactivationMigrationEvent event) {
        AnalyticService.update(event);
    }

    @EventListener
    @AnalyseTransaction(name = "transactionSnapshot")
    @ClientAware(clientAlias = "#event.transaction.clientAlias")
    public void onTransactionSnapshotEvent(TransactionSnapshotEvent event) {
        Optional.ofNullable(event.getPurchaseDetails()).ifPresent(AnalyticService::update);
        AnalyticService.update(UID, event.getTransaction().getUid());
        AnalyticService.update(MSISDN, event.getTransaction().getMsisdn());
        AnalyticService.update(PLAN_ID, event.getTransaction().getPlanId());
        AnalyticService.update(ITEM_ID, event.getTransaction().getItemId());
        AnalyticService.update(AMOUNT_PAID, event.getTransaction().getAmount());
        AnalyticService.update(CLIENT, event.getTransaction().getClientAlias());
        AnalyticService.update(COUPON_CODE, event.getTransaction().getCoupon());
        if(Objects.nonNull(event) && Objects.nonNull(event.getPurchaseDetails()) && Objects.nonNull(event.getPurchaseDetails().getGeoLocation())){
            AnalyticService.update(ACCESS_COUNTRY_CODE, event.getPurchaseDetails().getGeoLocation().getAccessCountryCode());
            AnalyticService.update(STATE_CODE, event.getPurchaseDetails().getGeoLocation().getStateCode());
            AnalyticService.update(IP, event.getPurchaseDetails().getGeoLocation().getIp());
        }
        if (EnumSet.of(PaymentEvent.SUBSCRIBE, PaymentEvent.RENEW).contains(event.getTransaction().getType()) && !IAP_PAYMENT_METHODS.contains(event.getTransaction().getPaymentChannel().name())) {
            AnalyticService.update(MANDATE_AMOUNT, event.getTransaction().getMandateAmount());
        }
        if (Objects.nonNull(event.getTransaction().getCoupon())) {
            String couponCode = event.getTransaction().getCoupon();
            CouponCodeLink couponLinkOption = BeanLocatorFactory.getBean(ICouponCodeLinkService.class).fetchCouponCodeLink(couponCode.toUpperCase(Locale.ROOT));
            if (couponLinkOption != null) {
                Coupon coupon = BeanLocatorFactory.getBean(CouponCachingService.class).get(couponLinkOption.getCouponId());
                if (!coupon.isCaseSensitive()) {
                    couponCode = couponCode.toUpperCase(Locale.ROOT);
                }
            }
            String couponId = BeanLocatorFactory.getBean(ICouponCodeLinkService.class).fetchCouponCodeLink(couponCode).getCouponId();
            Coupon coupon = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IEntityCacheService<Coupon, String>>() {
            }).get(couponId);
            AnalyticService.update(COUPON_GROUP, coupon.getId());
            AnalyticService.update(DISCOUNT_TYPE, coupon.getDiscountType().toString());
            AnalyticService.update(DISCOUNT_VALUE, coupon.getDiscount());
        }
        AnalyticService.update(TRANSACTION_ID, event.getTransaction().getIdStr());
        AnalyticService.update(INIT_TIMESTAMP, event.getTransaction().getInitTime().getTime().getTime());
        if (Objects.nonNull(event.getTransaction().getExitTime()))
            AnalyticService.update(EXIT_TIMESTAMP, event.getTransaction().getExitTime().getTime().getTime());
        AnalyticService.update(PAYMENT_EVENT, event.getTransaction().getType().getValue());
        AnalyticService.update(PAYMENT_CODE, event.getTransaction().getPaymentChannel().name());
        AnalyticService.update(TRANSACTION_STATUS, event.getTransaction().getStatus().getValue());
        AnalyticService.update(PAYMENT_METHOD, event.getTransaction().getPaymentChannel().getCode());
        if (event.getTransaction().getStatus() == TransactionStatus.SUCCESS) {
            final BaseTDRResponse tdr = paymentManager.getTDR(event.getTransaction().getIdStr());
            AnalyticService.update(TDR, tdr.getTdr());
        }
        if (Objects.nonNull(event.getPurchaseDetails()) && Objects.nonNull(event.getPurchaseDetails().getAppDetails()))
            AnalyticService.update(event.getPurchaseDetails().getAppDetails());
        publishBranchEvent(event);
    }

    @EventListener
    private void publishPaymentEventToBranch(PaymentsBranchEvent event) {
        Map<String, Object> map = new HashMap<>();
        if (event.getData() instanceof EventsWrapper) {
            map.putAll(branchMeta((EventsWrapper) event.getData()));
            publishBranchEvent(map, event.getEventName());
        }
    }

    private void publishBranchEvent(Map<String, Object> meta, String event) {
        meta.put(EVENT, event);
        BranchEvent branchEvent= AppUtils.from(BranchRawDataEvent.builder().data(meta).build());
        BeanLocatorFactory.getBean(IKinesisEventPublisher.class).publish(dpStream, branchEvent.getEvent_name(), branchEvent);
        log.debug("Transaction Snapshot Event {}", branchEvent);
    }

    private Map<String, Object> branchMeta(EventsWrapper eventsWrapper) {
        return mapper.convertValue(eventsWrapper, Map.class);
    }

    private void publishBranchEvent(TransactionSnapshotEvent event) {
        try {
            EventsWrapper.EventsWrapperBuilder eventsWrapperBuilder = EventsWrapper.builder();
            eventsWrapperBuilder.uid(event.getTransaction().getUid())
                    .planId(event.getTransaction().getPlanId())
                    .item(event.getTransaction().getItemId())
                    .coupon(event.getTransaction().getCoupon())
                    .clientAlias(event.getTransaction().getClientAlias())
                    .amount(event.getTransaction().getAmount())
                    .discount(event.getTransaction().getDiscount())
                    .transactionId(event.getTransaction().getIdStr())
                    .msisdn(event.getTransaction().getMsisdn())
                    .transactionStatus(event.getTransaction().getStatus().name())
                    .paymentCode(event.getTransaction().getPaymentChannel().name())
                    .paymentEvent(event.getTransaction().getType().name())
                    .triggerDate(EventsWrapper.getTriggerDate())
                    .transaction(event.getTransaction());
            if (Optional.ofNullable(event.getPurchaseDetails()).isPresent()) {
                eventsWrapperBuilder.appDetails(event.getPurchaseDetails().getAppDetails())
                        .paymentDetails(event.getPurchaseDetails().getPaymentDetails())
                        .productDetails(event.getPurchaseDetails().getProductDetails())
                        .userDetails(event.getPurchaseDetails().getUserDetails())
                        .geolocation(event.getPurchaseDetails().getGeoLocation())
                        .paymentMode(event.getPurchaseDetails().getPaymentDetails().getPaymentMode())
                        .optForAutoRenew(event.getPurchaseDetails().getPaymentDetails().isAutoRenew())
                        .os(event.getPurchaseDetails().getAppDetails().getOs())
                        .deviceId(event.getPurchaseDetails().getAppDetails().getDeviceId());
            }
            publishBranchEvent(branchMeta(eventsWrapperBuilder.build()), TRANSACTION_SNAPShOT_EVENT);


        } catch (Exception e) {
            log.error("error occurred while trying to build BranchRawDataEvent from payment Service", e);
        }
    }
}
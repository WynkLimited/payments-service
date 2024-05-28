package in.wynk.payment.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.Message;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.dao.entity.Coupon;
import in.wynk.coupon.core.dao.entity.CouponCodeLink;
import in.wynk.coupon.core.service.CouponCachingService;
import in.wynk.coupon.core.service.ICouponCodeLinkService;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.*;
import in.wynk.payment.core.service.InvoiceDetailsCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.gpbs.GooglePlayReportEvent;
import in.wynk.payment.dto.gpbs.acknowledge.queue.ExternalTransactionReportMessageManager;
import in.wynk.payment.dto.invoice.GenerateInvoiceKafkaMessage;
import in.wynk.payment.dto.invoice.InvoiceKafkaMessage;
import in.wynk.payment.dto.invoice.InvoiceRetryTask;
import in.wynk.payment.dto.point.GenerateItemKafkaMessage;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractPaymentSettlementResponse;
import in.wynk.payment.event.WaPayStateRespEvent;
import in.wynk.payment.event.common.*;
import in.wynk.payment.handler.CustomerWinBackHandler;
import in.wynk.payment.handler.InvoiceRetryTaskHandler;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.TaxUtils;
import in.wynk.queue.constant.QueueConstant;
import in.wynk.queue.dto.MessageThresholdExceedEvent;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.scheduler.task.dto.TaskDefinition;
import in.wynk.scheduler.task.service.ITaskScheduler;
import in.wynk.sms.common.message.SmsNotificationMessage;
import in.wynk.stream.producer.IKafkaEventPublisher;
import in.wynk.stream.producer.IKinesisEventPublisher;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.tinylytics.dto.BranchEvent;
import in.wynk.tinylytics.dto.BranchRawDataEvent;
import in.wynk.tinylytics.utils.AppUtils;
import in.wynk.wynkservice.api.service.WynkServiceDetailsCachingService;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.quartz.SimpleScheduleBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.exception.WynkErrorType.UT025;
import static in.wynk.exception.WynkErrorType.UT999;
import static in.wynk.payment.core.constant.PaymentConstants.AIRTEL_TV;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_CODE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.queue.constant.BeanConstant.MESSAGE_PAYLOAD;
import static in.wynk.tinylytics.constants.TinylyticsConstants.EVENT;
import static in.wynk.tinylytics.constants.TinylyticsConstants.TRANSACTION_SNAPShOT_EVENT;

@Slf4j
@Service
public class PaymentEventListener {
    private final TaxUtils taxUtils;
    private final ObjectMapper mapper;
    private final RetryRegistry retryRegistry;
    private final PaymentManager paymentManager;
    private final PaymentGatewayManager paymentGatewayManager;
    private final ITaskScheduler<TaskDefinition<?>> taskScheduler;
    private final ISqsManagerService<Object> sqsManagerService;
    private final IPaymentErrorService paymentErrorService;
    private final ApplicationEventPublisher eventPublisher;
    private final IClientCallbackService clientCallbackService;
    private final ITransactionManagerService transactionManagerService;
    private final IMerchantTransactionService merchantTransactionService;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final PaymentCachingService cachingService;
    private final IQuickPayLinkGenerator quickPayLinkGenerator;
    private final InvoiceService invoiceService;
    private final IKafkaEventPublisher<String, InvoiceKafkaMessage> invoiceKafkaPublisher;
    private final IKafkaEventPublisher<String, WaPayStateRespEvent> paymentStatusKafkaPublisher;
    private final InvoiceDetailsCachingService invoiceDetailsCachingService;
    private final ClientDetailsCachingService clientDetailsCachingService;
    private final WynkServiceDetailsCachingService wynkServiceDetailsCachingService;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final IKafkaEventPublisher<String, GenerateItemKafkaMessage> itemKafkaPublisher;

    public static Map<String, String> map = Collections.singletonMap(AIRTEL_TV, AIRTEL_XSTREAM);

    @Value("${event.stream.dp}")
    private String dpStream;
    @Value("${wynk.kafka.producers.payment.status.topic}")
    private String waPayStateRespEventTopic;
    @Value("${wynk.multi.kafka.templates.item.topic}")
    private String itemTopic;
    @Value("${payment.cancelMandate.unsupported}")
    private List<String> cancelMandatePG;

    public PaymentEventListener (TaxUtils taxUtils, ObjectMapper mapper, RetryRegistry retryRegistry, PaymentManager paymentManager,
                                 PaymentGatewayManager paymentGatewayManager, ITaskScheduler<TaskDefinition<?>> taskScheduler,
                                 ISqsManagerService<Object> sqsManagerService, IPaymentErrorService paymentErrorService, ApplicationEventPublisher eventPublisher,
                                 IClientCallbackService clientCallbackService, ITransactionManagerService transactionManagerService,
                                 IMerchantTransactionService merchantTransactionService, IRecurringPaymentManagerService recurringPaymentManagerService,
                                 PaymentCachingService cachingService, IQuickPayLinkGenerator quickPayLinkGenerator, InvoiceService invoiceService,
                                 IKafkaEventPublisher<String, InvoiceKafkaMessage> invoiceKafkaPublisher,
                                 IKafkaEventPublisher<String, WaPayStateRespEvent> paymentStatusKafkaPublisher, InvoiceDetailsCachingService invoiceDetailsCachingService,
                                 ClientDetailsCachingService clientDetailsCachingService, WynkServiceDetailsCachingService wynkServiceDetailsCachingService,
                                 ISubscriptionServiceManager subscriptionServiceManager,
                                 @Qualifier("item")
                                 @Lazy
                                 IKafkaEventPublisher<String, GenerateItemKafkaMessage> itemKafkaPublisher) {
        this.taxUtils = taxUtils;
        this.mapper = mapper;
        this.retryRegistry = retryRegistry;
        this.paymentManager = paymentManager;
        this.paymentGatewayManager = paymentGatewayManager;
        this.taskScheduler = taskScheduler;
        this.sqsManagerService = sqsManagerService;
        this.paymentErrorService = paymentErrorService;
        this.eventPublisher = eventPublisher;
        this.clientCallbackService = clientCallbackService;
        this.transactionManagerService = transactionManagerService;
        this.merchantTransactionService = merchantTransactionService;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
        this.cachingService = cachingService;
        this.quickPayLinkGenerator = quickPayLinkGenerator;
        this.invoiceService = invoiceService;
        this.invoiceKafkaPublisher = invoiceKafkaPublisher;
        this.paymentStatusKafkaPublisher = paymentStatusKafkaPublisher;
        this.invoiceDetailsCachingService = invoiceDetailsCachingService;
        this.clientDetailsCachingService = clientDetailsCachingService;
        this.wynkServiceDetailsCachingService = wynkServiceDetailsCachingService;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.itemKafkaPublisher = itemKafkaPublisher;
    }

    @EventListener
    @AnalyseTransaction(name = QueueConstant.DEFAULT_SQS_MESSAGE_THRESHOLD_EXCEED_EVENT)
    public void onAnyOrderMessageThresholdExceedEvent (MessageThresholdExceedEvent event) throws JsonProcessingException {
        AnalyticService.update(event);
        AnalyticService.update(MESSAGE_PAYLOAD, mapper.writeValueAsString(event));
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "paymentReconciliationThresholdExceedEvent")
    public void onPaymentReconThresholdExceedEvent (PaymentReconciliationThresholdExceedEvent event) {
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
    public void onPaymentRenewalMessageThresholdExceedEvent (PaymentRenewalMessageThresholdExceedEvent event) {
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
    @AnalyseTransaction(name = "recurringPaymentEvent")
    public void onRecurringPaymentEvent (RecurringPaymentEvent event) {
        AnalyticService.update(event);
        Transaction transaction = event.getTransaction();
        if ((!cancelMandatePG.contains(transaction.getPaymentChannel().getId())) && (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.UNSUBSCRIBE) &&
                event.getPaymentEvent() == PaymentEvent.UNSUBSCRIBE) {
            cancelMandateFromPG(transaction.getPaymentChannel().getId(), transaction.getPaymentChannel().getCode(), transaction.getIdStr());
        }
    }

    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "cancelMandateFromPGEvent")
    private void cancelMandateFromPG (String paymentCode, String paymentMethod, String txnId) {
        try {
            AnalyticService.update("paymentCode", paymentCode);
            AnalyticService.update("txnId", txnId);
            BeanLocatorFactory.getBean(paymentMethod, ICancellingRecurringService.class).cancelRecurring(txnId);
        } catch (Exception e) {
            AnalyticService.update("isMandateCancelled", false);
            log.error(PaymentLoggingMarker.MANDATE_REVOKE_ERROR, e.getMessage(), e);
        }
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "merchantTransactionEvent")
    public void onMerchantTransactionEvent (MerchantTransactionEvent event) {
        AnalyticService.update(event);
        //If we get data in  merchant table then update the data else upsert the data
        boolean isDataPresent = checkIfEntryPresentAndUpdateData(event);
        if (!isDataPresent) {
            MerchantTransaction merchantTransaction = MerchantTransaction.builder()
                    .id(event.getId())
                    .externalTransactionId(event.getExternalTransactionId())
                    .request(event.getRequest())
                    .response(event.getResponse())
                    .externalTokenReferenceId(event.getExternalTokenReferenceId())
                    .orderId(event.getOrderId())
                    .build();
            upsertData(merchantTransaction, event);
        }
    }

    private void upsertData (MerchantTransaction merchantTransaction, MerchantTransactionEvent event) {
        try {
            retryRegistry.retry(PaymentConstants.MERCHANT_TRANSACTION_UPSERT_RETRY_KEY).executeRunnable(() -> merchantTransactionService.upsert(merchantTransaction));
        } catch (Exception e) {
            log.error("Exception occurred while saving data in merchant table {} {}", event, e.getMessage());
        }
    }

    private boolean checkIfEntryPresentAndUpdateData (MerchantTransactionEvent event) {
        try {
            if (Objects.isNull(event.getExternalTokenReferenceId())) {
                MerchantTransaction merchantData = merchantTransactionService.getMerchantTransaction(event.getId());
                merchantData.setRequest(event.getRequest());
                merchantData.setResponse(event.getResponse());
                merchantData.setExternalTransactionId(event.getExternalTransactionId());
                merchantData.setOrderId(merchantData.getOrderId());
                merchantData.setExternalTokenReferenceId(merchantData.getExternalTokenReferenceId());
                upsertData(merchantData, event);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @EventListener
    @AnalyseTransaction(name = "paymentErrorEvent")
    @ClientAware(clientAlias = "#event.clientAlias")
    public void onPaymentErrorEvent (PaymentErrorEvent event) {
        AnalyticService.update(event);
        publishAdditionalData(event);
        retryRegistry.retry(PaymentConstants.PAYMENT_ERROR_UPSERT_RETRY_KEY).executeRunnable(() -> paymentErrorService.upsert(PaymentError.builder()
                .id(event.getId())
                .code(Objects.nonNull(event.getCode()) ? event.getCode() : "UNKNOWN")
                .description(Objects.nonNull(event.getDescription()) && event.getDescription().length() > 255 ? event.getDescription().substring(0, Math.min(event.getDescription().length(), 255)) :
                        "No description found")
                .build()));
    }

    private void publishAdditionalData (PaymentErrorEvent event) {
        if (StringUtils.equals(event.getCode(), E6002) && Objects.isNull(event.getDescription())) {
            AnalyticService.update(DESCRIPTION, ERROR_DESCRIPTION_FOR_E6002);
        }
        Transaction transaction = transactionManagerService.get(event.getId());
        if (Objects.nonNull(transaction)) {
            AnalyticService.update(UID, transaction.getUid());
            if (Objects.nonNull(transaction.getPlanId())) {
                AnalyticService.update(PLAN_ID, transaction.getPlanId());
            }
            if (Objects.nonNull(transaction.getItemId())) {
                AnalyticService.update(ITEM_ID, transaction.getItemId());
            }
            AnalyticService.update(PAYMENT_CODE, transaction.getPaymentChannel().getId());
            AnalyticService.update(PAYMENT_EVENT, transaction.getType().getValue());
        }
        if (transaction.getType() == PaymentEvent.RENEW) {
            PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(event.getId());
            if (Objects.nonNull(renewal)) {
                AnalyticService.update(RENEWAL_ATTEMPT_SEQUENCE, renewal.getAttemptSequence());
            }
        }
    }

    @EventListener
    @AnalyseTransaction(name = "invoiceEvent")
    public void onInvoiceEvent (InvoiceEvent event) {
        AnalyticService.update(event);
        AnalyticService.update(INVOICE_ID, event.getInvoice().getId());
        AnalyticService.update(TRANSACTION_ID, event.getInvoice().getTransactionId());
        AnalyticService.update("invoiceExternalId", event.getInvoice().getInvoiceExternalId());
        AnalyticService.update("amount", event.getInvoice().getAmount());
        AnalyticService.update("taxAmount", event.getInvoice().getTaxAmount());
        AnalyticService.update("taxableValue", event.getInvoice().getTaxableValue());
        AnalyticService.update("cgst", event.getInvoice().getCgst());
        AnalyticService.update("sgst", event.getInvoice().getSgst());
        AnalyticService.update("igst", event.getInvoice().getIgst());
        AnalyticService.update("customerAccountNo", event.getInvoice().getCustomerAccountNumber());
        AnalyticService.update("status", event.getInvoice().getStatus());
        AnalyticService.update("description", event.getInvoice().getDescription());
        AnalyticService.update("createdOn", event.getInvoice().getCreatedOn().getTimeInMillis());
        if (Objects.nonNull(event.getInvoice().getUpdatedOn())) {
            AnalyticService.update("updatedOn", event.getInvoice().getUpdatedOn().getTime().getTime());
        }
        AnalyticService.update("retryCount", event.getInvoice().getRetryCount());
    }

    @EventListener
    @AnalyseTransaction(name = "scheduleInvoiceRetryEvent")
    public void onInvoiceRetryEvent (InvoiceRetryEvent event) {
        try {
            AnalyticService.update(event);
            int retryCount = event.getRetryCount();
            final Invoice invoice = invoiceService.getInvoiceByTransactionId(event.getTxnId());
            if (Objects.nonNull(invoice)) {
                retryCount = invoice.getRetryCount();
            }
            if (retryCount < event.getRetries().size()) {
                final long attemptDelayedBy = event.getRetries().get(retryCount);
                final Date taskScheduleTime = new Date(System.currentTimeMillis() + (attemptDelayedBy * 1000));
                taskScheduler.schedule(TaskDefinition.<InvoiceRetryTask>builder()
                        .entity(InvoiceRetryTask.from(event))
                        .handler(InvoiceRetryTaskHandler.class)
                        .triggerConfiguration(TaskDefinition.TriggerConfiguration.builder()
                                .durable(false)
                                .startAt(taskScheduleTime)
                                .scheduleBuilder(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0).withIntervalInSeconds(0))
                                .build())
                        .build());
            }
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.INVOICE_SCHEDULE_RETRY_FAILURE, "Unable to schedule the invoice retry due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY448, e);
        }
    }

    @EventListener
    @AnalyseTransaction(name = "triggerInvoiceRetryEvent")
    public void onInvoiceRetryTaskEvent (InvoiceRetryTaskEvent event) {
        try {
            AnalyticService.update(event);
            final Invoice invoice = invoiceService.getInvoiceByTransactionId(event.getTransactionId());
            if (Objects.nonNull(invoice)) {
                if (!invoice.getStatus().equalsIgnoreCase("SUCCESS")) {
                    final GenerateInvoiceEvent generateInvoiceEvent = GenerateInvoiceEvent.builder()
                            .msisdn(event.getMsisdn())
                            .txnId(event.getTransactionId())
                            .clientAlias(event.getClientAlias())
                            .build();
                    final Transaction transaction = transactionManagerService.get(event.getTransactionId());
                    final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
                    final String clientAlias = clientDetailsCachingService.getClientByService(plan.getService()).getAlias();
                    invoiceKafkaPublisher.publish(GenerateInvoiceKafkaMessage.from(generateInvoiceEvent, clientAlias));
                    invoice.setRetryCount(invoice.getRetryCount() + 1);
                    invoice.persisted();
                    invoiceService.upsert(invoice);

                    final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(event.getClientAlias());
                    if (event.getRetryCount() + 1 < invoiceDetails.getRetries().size()) {
                        eventPublisher.publishEvent(InvoiceRetryEvent.builder()
                                .msisdn(event.getMsisdn())
                                .clientAlias(event.getClientAlias())
                                .txnId(event.getTransactionId())
                                .retries(invoiceDetails.getRetries())
                                .retryCount(event.getRetryCount() + 1).build());
                    }
                    ;
                }
            } else {
                final GenerateInvoiceEvent generateInvoiceEvent = GenerateInvoiceEvent.builder()
                        .msisdn(event.getMsisdn())
                        .txnId(event.getTransactionId())
                        .clientAlias(event.getClientAlias())
                        .build();
                final Transaction transaction = transactionManagerService.get(event.getTransactionId());
                final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
                final String clientAlias = clientDetailsCachingService.getClientByService(plan.getService()).getAlias();
                invoiceKafkaPublisher.publish(GenerateInvoiceKafkaMessage.from(generateInvoiceEvent, clientAlias));

                final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(event.getClientAlias());
                if (event.getRetryCount() + 1 < invoiceDetails.getRetries().size()) {
                    eventPublisher.publishEvent(InvoiceRetryEvent.builder()
                            .msisdn(event.getMsisdn())
                            .clientAlias(event.getClientAlias())
                            .txnId(event.getTransactionId())
                            .retries(invoiceDetails.getRetries())
                            .retryCount(event.getRetryCount() + 1).build());
                }
            }
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.INVOICE_TRIGGER_RETRY_FAILURE, "Unable to trigger the invoice retry due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY449, e);
        }
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "paymentRefundInitEvent")
    public void onPaymentRefundInitEvent (PaymentRefundInitEvent event) {
        AnalyticService.update(event);
        sqsManagerService.publishSQSMessage(PaymentRefundInitMessage.builder()
                .originalTransactionId(event.getOriginalTransactionId())
                .reason(event.getReason())
                .build());
    }

    @EventListener
    @AnalyseTransaction(name = "paymentRefundedEvent")
    public void onPaymentRefundEvent (PaymentRefundReconciledEvent event) {
        AnalyticService.update(event);
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "paymentReconciledEvent")
    public void onPaymentReconciledEvent (PaymentChargingReconciledEvent event) {
        AnalyticService.update(event);
        initRefundIfApplicable(event);
    }

    @EventListener
    @AnalyseTransaction(name = "clientCallback")
    @ClientAware(clientAlias = "#event.clientAlias")
    public void onClientCallbackEvent (ClientCallbackEvent callbackEvent) {
        AnalyticService.update(callbackEvent);
        sendClientCallback(callbackEvent.getClientAlias(), ClientCallbackRequest.from(callbackEvent));
    }


    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "purchaseInitEvent")
    public void onPurchaseInitEvent (PurchaseInitEvent event) {
        try {
            AnalyticService.update(event);
            PurchaseRecord purchaseRecord = PurchaseRecord.from(event);
            final ClientDetails clientDetails = (ClientDetails) ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
            if (taskScheduler.isTriggerExist(purchaseRecord.getGroupId(), purchaseRecord.getTaskId())) {
                taskScheduler.unSchedule(purchaseRecord.getGroupId(), purchaseRecord.getTaskId());
            }
            final long delayedBy = (clientDetails.<Double>getMeta(PaymentConstants.PAYMENT_DROPOUT_TRACKER_IN_SECONDS).orElse(3600D)).longValue();
            final Date taskScheduleTime = new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delayedBy));
            taskScheduler.schedule(TaskDefinition.<PurchaseRecord>builder()
                    .entity(purchaseRecord)
                    .handler(CustomerWinBackHandler.class)
                    .triggerConfiguration(TaskDefinition.TriggerConfiguration.builder()
                            .durable(false)
                            .startAt(taskScheduleTime)
                            .scheduleBuilder(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0).withIntervalInSeconds(0))
                            .build())
                    .build());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYMENT_DROP_OUT_NOTIFICATION_FAILURE, "Unable to schedule the drop out notification due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY047, e);
        }
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "purchaseRecordEvent")
    public void onPurchaseRecordEvent (PurchaseRecordEvent event) {
        try {
            AnalyticService.update(event);
            final Transaction lastTransaction = transactionManagerService.get(event.getTransactionId());
            final boolean sendDropOutNotification = lastTransaction.getStatus() != TransactionStatus.SUCCESS;
            if (!sendDropOutNotification) {
                log.info("Skipping to send drop out notification as user has completed transaction for {}", event);
                return;
            }
            final String tinyUrl = quickPayLinkGenerator.generate(event.getTransactionId(), event.getClientAlias(), event.getSid(), event.getAppDetails(), event.getProductDetails());
            AnalyticService.update(WINBACK_NOTIFICATION_URL, tinyUrl);
            sendNotificationToUser(event.getProductDetails(), tinyUrl, event.getMsisdn(), lastTransaction.getStatus());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYMENT_DROP_OUT_NOTIFICATION_FAILURE, "Unable to trigger the drop out notification due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY047, e);
        }
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "paymentAutoRefundEvent")
    public void onPaymentAutoRefundEvent (PaymentAutoRefundEvent event) {
        try {
            AnalyticService.update(event);
            final String tinyUrl = quickPayLinkGenerator.generate(event.getTransaction().getIdStr(), event.getClientAlias(), event.getPurchaseDetails().getAppDetails(),
                    event.getPurchaseDetails().getProductDetails());
            AnalyticService.update(WINBACK_NOTIFICATION_URL, tinyUrl);
            sendNotificationToUser(event.getPurchaseDetails().getProductDetails(), tinyUrl, event.getTransaction().getMsisdn(), event.getTransaction().getStatus());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYMENT_AUTO_REFUND_NOTIFICATION_FAILURE, "Unable to trigger the payment auto refund notification due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY048, e);
        }
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "generateInvoiceEvent")
    public void onGenerateInvoiceEvent (GenerateInvoiceEvent event) {
        try {
            AnalyticService.update(event);
            final Invoice invoice = invoiceService.getInvoiceByTransactionId(event.getTxnId());
            if (Objects.isNull(invoice)) {
                final Transaction transaction = transactionManagerService.get(event.getTxnId());
                String clientAlias;
                if (transaction.getType() == PaymentEvent.POINT_PURCHASE) {
                    clientAlias = transaction.getClientAlias();
                } else {
                    final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
                    clientAlias = clientDetailsCachingService.getClientByService(plan.getService()).getAlias();
                }
                invoiceKafkaPublisher.publish(GenerateInvoiceKafkaMessage.from(event, clientAlias));
            }
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.KAFKA_PUBLISHER_FAILURE, "Unable to publish the generate invoice event in kafka due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY452, e);
        }
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    @AnalyseTransaction(name = "externalTransactionReportEvent")
    public void onExternalTransactionReportEvent (ExternalTransactionReportEvent event) {
        AnalyticService.update(event);
        try {
            sqsManagerService.publishSQSMessage(ExternalTransactionReportMessageManager.builder().clientAlias(event.getClientAlias()).transactionId(event.getTransactionId())
                    .externalTransactionId(event.getExternalTokenReferenceId()).paymentEvent(event.getPaymentEvent()).initialTransactionId(event.getInitialTransactionId()).build());
        } catch (Exception e) {
            log.error("Exception occurred while publishing event on ExternalTransactionReport queue for transactionId: {}", event.getTransactionId(), e);
        }
    }

    @EventListener
    @AnalyseTransaction(name = "GooglePlayReportEvent")
    public void onGooglePlayReportEvent (GooglePlayReportEvent event) {
        AnalyticService.update(event);
    }

    private void sendNotificationToUser (IProductDetails productDetails, String tinyUrl, String msisdn, TransactionStatus txnStatus) {
        final PlanDTO plan = cachingService.getPlan(productDetails.getId());
        final String service = productDetails.getType().equalsIgnoreCase(PLAN) ? plan.getService() : cachingService.getItem(productDetails.getId()).getService();
        final WynkService wynkService = WynkServiceUtils.fromServiceId(service);
        final Message message = wynkService.getMessages().get(PaymentConstants.USER_WINBACK).get(txnStatus.getValue());
        if ((Objects.nonNull(message)) && message.isEnabled()) {
            Map<String, Object> contextMap = new HashMap<String, Object>() {{
                put(PLAN, plan);
                put(OFFER, cachingService.getOffer(plan.getLinkedOfferId()));
                put(WINBACK_NOTIFICATION_URL, tinyUrl);
            }};
            SmsNotificationMessage notificationMessage = SmsNotificationMessage.builder()
                    .messageId(message.getMessageId())
                    .msisdn(msisdn)
                    .service(service)
                    .contextMap(contextMap)
                    .build();
            sqsManagerService.publishSQSMessage(notificationMessage);
        } else {
            log.info("Skipping to send drop out notification for msisdn {} as it has been disabled", msisdn);
        }
    }


    @EventListener
    @AnalyseTransaction(name = "mandateStatusEvent")
    private void onMandateStatusEvent (MandateStatusEvent event) {
        AnalyticService.update(event);
    }

    private void sendClientCallback (String clientAlias, ClientCallbackRequest request) {
        clientCallbackService.sendCallback(ClientCallbackPayloadWrapper.<ClientCallbackRequest>builder().clientAlias(clientAlias).payload(request).build());
    }

    private void initRefundIfApplicable (PaymentChargingReconciledEvent event) {
        if (EnumSet.of(TransactionStatus.SUCCESS).contains(event.getTransactionStatus()) && event.getPaymentGateway().isTrialRefundSupported() &&
                (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(event.getPaymentEvent()))) {
            eventPublisher.publishEvent(PaymentRefundInitEvent.builder()
                    .reason("trial plan amount refund")
                    .originalTransactionId(event.getTransactionId())
                    .build());
        }
    }

    @EventListener
    @AnalyseTransaction(name = "paymentSettlement")
    public void onPaymentSettlementEvent (PaymentSettlementEvent event) {
        final AbstractPaymentSettlementResponse response = paymentManager.settle(PaymentSettlementRequest.builder().tid(event.getTid()).build());
        AnalyticService.update(event);
        AnalyticService.update(response);
    }

    @EventListener
    @AnalyseTransaction(name = "userSubscriptionStatus")
    @TransactionAware(txnId = "#event.transactionId", lock = false)
    public void onUserSubscriptionEvent (UserSubscriptionStatusEvent event) {
        AnalyticService.update(event);
        if (!SUBSCRIBED_STATE.equals(event.getStatus())) {
            Transaction transaction = TransactionContext.get();
            transaction.setType(PaymentEvent.UNSUBSCRIBE.getValue());
            AsyncTransactionRevisionRequest request =
                    AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(transaction.getStatus()).finalTransactionStatus(TransactionStatus.CANCELLED).build();
            subscriptionServiceManager.unSubscribePlan(AbstractUnSubscribePlanRequest.from(request));
        }
    }

    @EventListener
    @ClientAware(clientAlias = "#event.clientAlias")
    public void onPaymentUserDeactivationEvent (PaymentUserDeactivationEvent event) {
        try {
            transactionManagerService.migrateOldTransactions(event.getId(), event.getUid(), event.getOldUid(), event.getService());
        } catch (Exception e) {
            throw new WynkRuntimeException(UT999, e);
        }
    }

    @EventListener
    @AnalyseTransaction(name = "paymentUserDeactivationMigrationEvent")
    public void onPaymentUserDeactivationMigrationEvent (PaymentUserDeactivationMigrationEvent event) {
        AnalyticService.update(event);
    }

    @EventListener
    @AnalyseTransaction(name = "transactionSnapshot")
    @ClientAware(clientAlias = "#event.transaction.clientAlias")
    public void onTransactionSnapshotEvent (TransactionSnapshotEvent event) {
        Optional.ofNullable(event.getPurchaseDetails()).ifPresent(AnalyticService::update);
        AnalyticService.update(UID, event.getTransaction().getUid());
        AnalyticService.update(MSISDN, event.getTransaction().getMsisdn());
        AnalyticService.update(PLAN_ID, event.getTransaction().getPlanId());
        AnalyticService.update(ITEM_ID, event.getTransaction().getItemId());
        AnalyticService.update(AMOUNT_PAID, event.getTransaction().getAmount());
        AnalyticService.update(CLIENT, event.getTransaction().getClientAlias());
        AnalyticService.update(COUPON_CODE, event.getTransaction().getCoupon());
        if (Objects.nonNull(event.getPurchaseDetails()) && Objects.nonNull(event.getPurchaseDetails().getGeoLocation())) {
            AnalyticService.update(ACCESS_COUNTRY_CODE, event.getPurchaseDetails().getGeoLocation().getAccessCountryCode());
            AnalyticService.update(STATE_CODE, event.getPurchaseDetails().getGeoLocation().getStateCode());
            AnalyticService.update(IP, event.getPurchaseDetails().getGeoLocation().getIp());
        }
        if (EnumSet.of(PaymentEvent.SUBSCRIBE, PaymentEvent.RENEW).contains(event.getTransaction().getType()) && !IAP_PAYMENT_METHODS.contains(event.getTransaction().getPaymentChannel().name())) {
            AnalyticService.update(MANDATE_AMOUNT, event.getTransaction().getMandateAmount());
            String referenceTransactionId = event.getTransaction().getIdStr();
            int renewalAttemptSequence = 0;
            if (PaymentEvent.RENEW == event.getTransaction().getType()) {
                PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(event.getTransaction().getIdStr());
                if (renewal != null) {
                    renewalAttemptSequence = renewal.getAttemptSequence();
                    referenceTransactionId = renewal.getInitialTransactionId();
                }
            }
            AnalyticService.update(RENEWAL_ATTEMPT_SEQUENCE, renewalAttemptSequence);
            AnalyticService.update(REFERENCE_TRANSACTION_ID, referenceTransactionId);
        }
        if (PaymentEvent.RENEW.equals(event.getTransaction().getType())) {
            if (Objects.nonNull(event.getPurchaseDetails()) && Objects.nonNull(event.getPurchaseDetails().getAppDetails())) {
                AnalyticService.update(SERVICE, event.getPurchaseDetails().getAppDetails().getService());
            }
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
        if (Objects.nonNull(event.getTransaction().getExitTime())) {
            AnalyticService.update(EXIT_TIMESTAMP, event.getTransaction().getExitTime().getTime().getTime());
        }
        AnalyticService.update(PAYMENT_EVENT, event.getTransaction().getType().getValue());
        AnalyticService.update(PAYMENT_CODE, event.getTransaction().getPaymentChannel().name());
        AnalyticService.update(TRANSACTION_STATUS, event.getTransaction().getStatus().getValue());
        AnalyticService.update(PAYMENT_METHOD, event.getTransaction().getPaymentChannel().getCode());
        if (event.getTransaction().getStatus() == TransactionStatus.SUCCESS) {
            final BaseTDRResponse tdr = paymentGatewayManager.getTDR(event.getTransaction().getIdStr());
            AnalyticService.update(TDR, tdr.getTdr());
            //Invoice should not be generated for Trial or mandate subscription WCF-4350
            if ((PaymentEvent.MANDATE != event.getTransaction().getType() && PaymentEvent.TRIAL_SUBSCRIPTION != event.getTransaction().getType()) &&
                    event.getTransaction().getPaymentChannel().isInvoiceSupported()) {
                if (PaymentEvent.REFUND != event.getTransaction().getType() || (PaymentEvent.REFUND == event.getTransaction().getType() && event.getTransaction().getAmount() != MANDATE_FLOW_AMOUNT)) {
                    eventPublisher.publishEvent(GenerateInvoiceEvent.builder()
                            .msisdn(event.getTransaction().getMsisdn())
                            .txnId(event.getTransaction().getIdStr())
                            .clientAlias(event.getTransaction().getClientAlias())
                            .build());
                }
            }
        }
        if (Objects.nonNull(event.getPurchaseDetails()) && Objects.nonNull(event.getPurchaseDetails().getAppDetails())) {
            AnalyticService.update(event.getPurchaseDetails().getAppDetails());
        }
        if (event.getTransaction().getStatus().equals(TransactionStatus.AUTO_REFUND)) {
            eventPublisher.publishEvent(PaymentAutoRefundEvent.builder()
                    .transaction(event.getTransaction())
                    .clientAlias(event.getTransaction().getClientAlias())
                    .purchaseDetails(event.getPurchaseDetails())
                    .build());
        }
        publishBranchEvent(event);
        if (EnumSet.of(TransactionStatus.SUCCESS, TransactionStatus.FAILURE).contains(event.getTransaction().getStatus())) {
            publishWaPaymentStatusEvent(event);
        }
    }

    @EventListener
    @AnalyseTransaction(name = "generateItemEvent")
    public void onGenerateItemEvent(GenerateItemEvent event) {
        itemKafkaPublisher.publish(itemTopic, null, System.currentTimeMillis(), null, GenerateItemKafkaMessage.from(event), null);
        AnalyticService.update(event);
    }

    public void publishWaPaymentStatusEvent (TransactionSnapshotEvent event) {
        final IPurchaseDetails purchaseDetails = event.getPurchaseDetails();
        if (purchaseDetails != null && purchaseDetails.getSessionDetails() != null && WhatsappSessionDetails.class.isAssignableFrom(purchaseDetails.getSessionDetails().getClass())) {
            final WhatsappSessionDetails waSessionDetails = (WhatsappSessionDetails) purchaseDetails.getSessionDetails();
            final List<Header> headers = new ArrayList() {{
                add(new RecordHeader(BaseConstants.ORG_ID, waSessionDetails.getOrgId().getBytes()));
                add(new RecordHeader(BaseConstants.SESSION_ID, waSessionDetails.getSessionId().getBytes()));
                add(new RecordHeader(BaseConstants.SERVICE_ID, waSessionDetails.getServiceId().getBytes()));
                add(new RecordHeader(BaseConstants.REQUEST_ID, waSessionDetails.getRequestId().getBytes()));
            }};

            final PlanDTO selectedPlan = cachingService.getPlan(event.getTransaction().getPlanId());
            final WaPayStateRespEvent.WaPayStateRespEventBuilder builder = WaPayStateRespEvent.builder()
                    .sessionId(waSessionDetails.getSessionId())
                    .to(waSessionDetails.getFrom())
                    .from(waSessionDetails.getTo())
                    .campaignId(waSessionDetails.getCampaignId())
                    .planDetails(EligiblePlanDetails.builder()
                            .title(selectedPlan.getTitle())
                            .id(String.valueOf(selectedPlan.getId()))
                            .description(selectedPlan.getDescription())
                            .priceDetails(PriceDetails.builder().currency(selectedPlan.getPrice().getCurrency()).price((int) selectedPlan.getPrice().getDisplayAmount())
                                    .discountPrice((int) selectedPlan.getPrice().getAmount()).build())
                            .periodDetails(PeriodDetails.builder().validity(selectedPlan.getPeriod().getValidity()).validityUnit(selectedPlan.getPeriod().getTimeUnit()).build())
                            .build());

            if (TransactionStatus.SUCCESS == event.getTransaction().getStatus()) {
                builder.orderDetails(WaOrderDetails.builder()
                        .taxDetails(TaxDetails.builder().value(taxUtils.calculateTax(event.getTransaction())).build())
                        .mandate(event.getTransaction().getMandateAmount() > -1)
                        .mandateAmount(event.getTransaction().getMandateAmount())
                        .status(event.getTransaction().getStatus().getValue())
                        .discount(event.getTransaction().getDiscount())
                        .event(event.getTransaction().getType().getValue())
                        .code(event.getPurchaseDetails().getPaymentDetails().getPaymentId())
                        .pgCode(event.getTransaction().getPaymentChannel().getCode())
                        .amount(event.getTransaction().getAmount())
                        .trial(event.getPurchaseDetails().getPaymentDetails().isTrialOpted())
                        .id(event.getTransaction().getIdStr())
                        .build());
            } else {
                PaymentError paymentError = paymentErrorService.getPaymentError(event.getTransaction().getIdStr());
                builder.orderDetails(WaFailedOrderDetails.builder()
                        .id(event.getTransaction().getIdStr())
                        .status(event.getTransaction().getStatus().getValue())
                        .event(event.getTransaction().getType().getValue())
                        .errorCode("PAY001")
                        .errorMessage(Objects.nonNull(paymentError) ? paymentError.getDescription() : "Transaction is failed")
                        .build());
            }
            paymentStatusKafkaPublisher.publish(waPayStateRespEventTopic, null, System.currentTimeMillis(), null, builder.build(), headers);
        }
    }

    @EventListener
    private void publishPaymentEventToBranch (PaymentsBranchEvent event) {
        Map<String, Object> map = new HashMap<>();
        if (event.getData() instanceof EventsWrapper) {
            map.putAll(branchMeta((EventsWrapper) event.getData()));
            publishBranchEvent(map, event.getEventName());
        }
    }


    @ClientAware(clientAlias = "#event.clientAlias")
    @EventListener(UnScheduleRecurringPaymentEvent.class)
    private void unScheduleTransactionRecurring (UnScheduleRecurringPaymentEvent event) {
        AnalyticService.update(event);
        recurringPaymentManagerService.unScheduleRecurringPayment(event.getClientAlias(), event.getTransactionId(), PaymentEvent.CANCELLED);
    }

    private void publishBranchEvent (Map<String, Object> meta, String event) {
        meta.put(EVENT, event);
        BranchEvent branchEvent = AppUtils.from(BranchRawDataEvent.builder().data(meta).build());
        BeanLocatorFactory.getBean(IKinesisEventPublisher.class).publish(dpStream, branchEvent.getEvent_name(), branchEvent);
    }

    private Map<String, Object> branchMeta (EventsWrapper eventsWrapper) {
        return mapper.convertValue(eventsWrapper, Map.class);
    }

    private void publishBranchEvent (TransactionSnapshotEvent event) {
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
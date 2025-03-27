package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.PaymentRenewalDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.PaymentRenewalDetailsDao;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.stream.constant.StreamConstant;
import in.wynk.stream.constant.StreamMarker;
import in.wynk.stream.consumer.impl.AbstractKafkaEventConsumer;
import in.wynk.stream.producer.IKafkaPublisherService;
import in.wynk.stream.service.KafkaRetryHandlerService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.RenewalPlanEligibilityResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentConstants.RENEWALS_INELIGIBLE_PLANS;

@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentRenewalKafkaConsumer extends AbstractKafkaEventConsumer<String, PaymentRenewalMessage> {

    private final ITransactionManagerService transactionManager;
    private final RecurringTransactionUtils recurringTransactionUtils;
    private final IKafkaPublisherService kafkaPublisherService;

    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final PaymentCachingService cachingService;
    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PaymentRenewalMessage> kafkaRetryHandlerService;

    public PaymentRenewalKafkaConsumer (ITransactionManagerService transactionManager, RecurringTransactionUtils recurringTransactionUtils, IKafkaPublisherService kafkaPublisherService, ISubscriptionServiceManager subscriptionServiceManager, IRecurringPaymentManagerService recurringPaymentManagerService, PaymentCachingService cachingService, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.transactionManager = transactionManager;
        this.recurringTransactionUtils = recurringTransactionUtils;
        this.kafkaPublisherService = kafkaPublisherService;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
        this.cachingService = cachingService;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRenewalMessage")
    public void consume(PaymentRenewalMessage message) throws WynkRuntimeException {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_RENEWAL_QUEUE, "processing PaymentRenewalMessage for transactionId {}", message.getTransactionId());
        Transaction transaction = transactionManager.get(message.getTransactionId());
        if (isEligibleForRenewal(transaction)) {
            kafkaPublisherService.publishKafkaMessage(PaymentRenewalChargingMessage.builder()
                    .uid(transaction.getUid())
                    .id(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .msisdn(transaction.getMsisdn())
                    .clientAlias(transaction.getClientAlias())
                    .attemptSequence(message.getAttemptSequence())
                    .paymentCode(transaction.getPaymentChannel().getId())
                    .build());
        }
    }

    @KafkaListener(id = "paymentRenewalMessageListener", topics = "${wynk.kafka.consumers.listenerFactory.paymentRenewal[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.paymentRenewal[0].name}")
    protected void listenPaymentRenewalMessage(@Header(value = StreamConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE, required = false) String lastAttemptedSequence,
                                               @Header(value = StreamConstant.MESSAGE_CREATION_DATETIME, required = false) String createdAt,
                                               @Header(value = StreamConstant.MESSAGE_LAST_PROCESSED_DATETIME, required = false) String lastProcessedAt,
                                               @Header(value = StreamConstant.RETRY_COUNT, required = false) String retryCount,
                                               ConsumerRecord<String, PaymentRenewalMessage> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PaymentRenewalMessage - ", e);
            }
        }
    }

    private boolean isEligibleForRenewal (Transaction transaction) {
        if (!isPlanDeprecated(transaction)) {
            ResponseEntity<WynkResponse.WynkResponseWrapper<RenewalPlanEligibilityResponse>> response =
                    subscriptionServiceManager.renewalPlanEligibilityResponse(transaction.getPlanId(), transaction.getUid());
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData())) {
                RenewalPlanEligibilityResponse renewalPlanEligibilityResponse = response.getBody().getData();
                long today = System.currentTimeMillis();
                long furtherDefer = renewalPlanEligibilityResponse.getDeferredUntil() - today;
                if (subscriptionServiceManager.isDeferred(transaction.getPaymentChannel().getCode(), furtherDefer, false)) {
                    if (Objects.equals(transaction.getPaymentChannel().getCode(), ApsConstant.AIRTEL_PAY_STACK)) {
                        furtherDefer = furtherDefer - ((long) 2 * 24 * 60 * 60 * 1000);
                    }
                    recurringPaymentManagerService.unScheduleRecurringPayment(transaction, PaymentEvent.DEFERRED, today, furtherDefer);
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isPlanDeprecated(Transaction transaction) {
        try {
            final PlanDTO planDTO = cachingService.getPlan(transaction.getPlanId());
            Optional<PaymentRenewalDetails> mapping = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), PaymentRenewalDetailsDao.class).findById(planDTO.getService());
            if (mapping.isPresent() && mapping.get().get(RENEWALS_INELIGIBLE_PLANS).isPresent()) {
                final List<Integer> renewalsDeprecatedPlans = (List<Integer>) mapping.get().getMeta().get(RENEWALS_INELIGIBLE_PLANS);
                if (renewalsDeprecatedPlans.contains(transaction.getPlanId())) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    public void start() {
        if (enabled) {
            log.info("Starting Kafka consumption..." + this.getClass().getCanonicalName());
        }
    }

    @Override
    public void stop() {
        if (enabled) {
            log.info("Shutting down Kafka consumption..." + this.getClass().getCanonicalName());
            this.endpointRegistry.stop();
        }
    }
}
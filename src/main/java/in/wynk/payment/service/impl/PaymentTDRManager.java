package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentTDRDetails;
import in.wynk.payment.core.dao.repository.PaymentTDRDetailsDao;
import in.wynk.payment.dto.BaseTDRResponse;
import in.wynk.payment.dto.TdrProcessingMessage;
import in.wynk.payment.service.IPaymentTDRManager;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.stream.constant.StreamConstant;
import in.wynk.stream.producer.IKafkaPublisherService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

import static in.wynk.common.constant.BaseConstants.PLAN_ID;
import static in.wynk.common.constant.BaseConstants.REFERENCE_TRANSACTION_ID;
import static in.wynk.common.constant.BaseConstants.TRANSACTION_ID;
import static in.wynk.common.constant.BaseConstants.UID;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.PaymentConstants.TDR;

import in.wynk.payment.core.constant.PaymentLoggingMarker;

@Service
@Slf4j
public class PaymentTDRManager implements IPaymentTDRManager {

    @Autowired
    @Qualifier("paymentTdrDetailsDao")
    private PaymentTDRDetailsDao paymentTDRDetailsRepository;

    @Autowired
    @Qualifier(BeanConstant.PAYMENT_MANAGER_V2)
    private PaymentGatewayManager paymentGatewayManager;

    @Autowired
    private IKafkaPublisherService kafkaPublisherService;

    public void fetchTDR(String requestId, String clientAlias) {
        try {
            MDC.put(REQUEST_ID, requestId);
            AnalyticService.update(REQUEST_ID, requestId);
            int batchSize = 200;
            List<PaymentTDRDetails> paymentTDRDetailsList = paymentTDRDetailsRepository.fetchNextTransactionForProcessing(batchSize);
            if (paymentTDRDetailsList.isEmpty()) {
                log.info("No eligible TDR transactions found for processing.");
                return;
            }
            Map<String, String> messageHeaders = new HashMap<>();
            messageHeaders.put(StreamConstant.RETRY_COUNT, "0");

            for (PaymentTDRDetails txn : paymentTDRDetailsList) {
                txn.setStatus(PaymentConstants.INPROGRESS);
                txn.setUpdatedTimestamp(Calendar.getInstance());
                TdrProcessingMessage message = TdrProcessingMessage.builder()
                        .transactionId(txn.getTransactionId())
                        .clientAlias(clientAlias)
                        .paymentTDRDetails(txn)
                        .build();
                publishTdrTransaction(txn.getTransactionId(), message, messageHeaders);
                paymentTDRDetailsRepository.save(txn);
            }
            AnalyticService.update("publishTdrTransactionsCompleted", true);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.TDR_RECORDS_PUBLISHING_ERROR,
                    "Unexpected error during TDR fetch and publish processing for requestId: {}", requestId, e);
        } finally {
            MDC.remove(REQUEST_ID);
        }
    }

    @AnalyseTransaction(name = "publishTdrTransaction")
    private void publishTdrTransaction(String transactionId, TdrProcessingMessage tdrProcessingMessage, Map<String, String> messageHeaders) {
        try {
            AnalyticService.update("tdrProcessingMessage", tdrProcessingMessage.toString());
            kafkaPublisherService.publishKafkaMessage(transactionId, tdrProcessingMessage, messageHeaders);
            log.info("Kafka message published for transaction {}", transactionId);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.TDR_KAFKA_PUBLISH_ERROR,
                    "Failed to publish Kafka message for transaction {}", transactionId, e);
        }
    }

    @AnalyseTransaction(name = "processTransactionForTDR")
    public void processTransaction(PaymentTDRDetails transaction) {
        try {
            AnalyticService.update(UID, transaction.getUid());
            AnalyticService.update(PLAN_ID, transaction.getPlanId());
            AnalyticService.update(REFERENCE_TRANSACTION_ID, transaction.getReferenceId());
            AnalyticService.update(TRANSACTION_ID, transaction.getTransactionId());

            BaseTDRResponse tdr = paymentGatewayManager.getTDR(transaction.getTransactionId());
            if ((tdr.getTdr() != -1) && (tdr.getTdr() != -2)) {
                transaction.setTdr(tdr.getTdr());
            } else {
                log.warn(PaymentLoggingMarker.TDR_PROCESSING_WARNING,
                        "Invalid TDR value received for transaction {}: TDR={}", transaction.getTransactionId(), tdr.getTdr());
            }
            AnalyticService.update(TDR, tdr.getTdr());
            transaction.setStatus(PaymentConstants.COMPLETED);
            transaction.setUpdatedTimestamp(Calendar.getInstance());
            paymentTDRDetailsRepository.save(transaction);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.TDR_API_ERROR,
                    "Error while fetching TDR information from TDR API: {}",
                    transaction.getTransactionId(),
                    e);
            throw e;
        }
    }

    public void markTransactionAsFailed(PaymentTDRDetails transaction) {
        try {
            transaction.setStatus(PaymentConstants.FAILED);
            transaction.setUpdatedTimestamp(Calendar.getInstance());
            paymentTDRDetailsRepository.save(transaction);
            log.warn(PaymentLoggingMarker.TDR_PROCESSING_WARNING,
                    "Transaction marked as FAILED after retry limit reached: {}", transaction.getTransactionId());

        } catch (Exception e) {
            log.error(PaymentLoggingMarker.TDR_PROCESSING_ERROR,
                    "Failed to mark transaction {} as FAILED", transaction.getTransactionId(), e);
        }
    }
}
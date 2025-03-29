package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.PaymentTDRDetails;
import in.wynk.payment.core.dao.repository.PaymentTDRDetailsDao;
import in.wynk.payment.dto.BaseTDRResponse;
import in.wynk.payment.service.IPaymentTDRManager;
import in.wynk.payment.service.PaymentGatewayManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;


import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
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

    public void fetchTDR(String requestId) {
        try {
            MDC.put(REQUEST_ID, requestId);
            AnalyticService.update(REQUEST_ID, requestId);
            int batchSize = 50;
            int processedCount = 0;

            while (processedCount < batchSize) {
                Optional<PaymentTDRDetails> optionalTransaction = Optional.empty();
                try {
                    optionalTransaction = paymentTDRDetailsRepository.fetchNextTransactionForProcessing();
                    if (!optionalTransaction.isPresent()) {
                        log.info("No pending transactions to process.");
                        break;
                    }

                    PaymentTDRDetails txn = optionalTransaction.get();
                    processTransaction(txn);
                    paymentTDRDetailsRepository.save(txn);
                    log.info("Successfully processed txn: {}", txn.getTransactionId());
                    processedCount++;
                } catch (Exception e) {
                    log.error(PaymentLoggingMarker.TDR_PROCESSING_ERROR,
                            "Processing failed for txn: {}. Retrying after 2 minutes.",
                            optionalTransaction.isPresent() ? optionalTransaction.get().getTransactionId() : "unknown",
                            e);
                    optionalTransaction.ifPresent(this::retryTransaction);
                }
            }
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.TDR_PROCESSING_ERROR,
                    "Unexpected error in fetchTDR processing for request: {}",
                    requestId,
                    e);
        } finally {
            MDC.remove(REQUEST_ID);
        }
    }

    @AnalyseTransaction(name = "processTransactionForTDR")
    private void processTransaction(PaymentTDRDetails transaction) {
        try {
            AnalyticService.update(UID, transaction.getUid());
            AnalyticService.update(PLAN_ID, transaction.getPlanId());
            AnalyticService.update(REFERENCE_TRANSACTION_ID, transaction.getReferenceId());
            AnalyticService.update(TRANSACTION_ID, transaction.getTransactionId());

            BaseTDRResponse tdr = paymentGatewayManager.getTDR(transaction.getTransactionId());
            if ((tdr.getTdr() != -1) && (tdr.getTdr() != -2)) {
                AnalyticService.update(TDR, tdr.getTdr());
                transaction.setTdr(tdr.getTdr());
            }

            transaction.setIsProcessed(true);
            transaction.setUpdatedTimestamp(Calendar.getInstance());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.TDR_API_ERROR,
                    "Error while fetching TDR information from TDR API: {}",
                    transaction.getTransactionId(),
                    e);
            throw e;
        }
    }

    private void retryTransaction(PaymentTDRDetails transaction) {
        try {
            transaction.setExecutionTime(new Date(System.currentTimeMillis() + 120000));
            paymentTDRDetailsRepository.save(transaction);
            log.info("transaction: {} which got failed is rescheduled", transaction.getTransactionId());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.TDR_RETRY_ERROR,
                    "Failed to reschedule transaction: {}",
                    transaction.getTransactionId(),
                    e);
        }
    }
}
//@Service
//@Slf4j
//public class PaymentTDRManager implements IPaymentTDRManager {
//
//    @Autowired
//    @Qualifier("paymentTdrDetailsDao")
//    private PaymentTDRDetailsDao paymentTDRDetailsRepository;
//
//    @Autowired
//    @Qualifier(BeanConstant.PAYMENT_MANAGER_V2)
//    private PaymentGatewayManager paymentGatewayManager;
//
//    public void fetchTDR(String requestId) {
//        MDC.put(REQUEST_ID, requestId);
//        AnalyticService.update(REQUEST_ID, requestId);
//        int batchSize = 50;
//        int processedCount = 0;
//
//        while (processedCount < batchSize) {
//            Optional<PaymentTDRDetails> optionalTransaction = paymentTDRDetailsRepository.fetchNextTransactionForProcessing();
//            if (!optionalTransaction.isPresent()) {
//                log.info("No pending transactions to process.");
//                break;
//            }
//            PaymentTDRDetails txn = optionalTransaction.get();
//            try {
//                processTransaction(txn);
//                paymentTDRDetailsRepository.save(txn);
//                log.info("Successfully processed txn: {}", txn.getTransactionId());
//                processedCount++;
//            } catch (Exception e) {
//                log.error("Processing failed for txn: {}. Retrying after 2 minutes.", txn.getTransactionId(), e);
//                retryTransaction(txn);
//            }
//        }
//    }
//
//    @AnalyseTransaction(name = "processTransactionForTDR")
//    private void processTransaction(PaymentTDRDetails transaction) {
//        try {
//            AnalyticService.update(UID, transaction.getUid());
//            AnalyticService.update(PLAN_ID, transaction.getPlanId());
//            AnalyticService.update(REFERENCE_TRANSACTION_ID, transaction.getReferenceId());
//            AnalyticService.update(TRANSACTION_ID, transaction.getTransactionId());
//
//            BaseTDRResponse tdr = paymentGatewayManager.getTDR(transaction.getTransactionId());
//            if ((tdr.getTdr() != -1) && (tdr.getTdr() != -2)) {
//                AnalyticService.update(TDR, tdr.getTdr());
//            }
//
//            transaction.setIsProcessed(true);
//            transaction.setTdr(tdr.getTdr());
//            transaction.setUpdatedTimestamp(Calendar.getInstance());
//        } catch (Exception e) {
//            throw e;
//        }
//    }
//
//    private void retryTransaction(PaymentTDRDetails transaction) {
//        transaction.setExecutionTime(new Date(System.currentTimeMillis() + 120000));
//        paymentTDRDetailsRepository.save(transaction);
//    }
//}








//@Service
//@Slf4j
//public class PaymentTDRManager implements IPaymentTDRManager {
//
//    @Autowired
//    @Qualifier("paymentTdrDetailsDao")
//    private PaymentTDRDetailsDao paymentTDRDetailsRepository;
//
//    @Autowired
//    @Qualifier(BeanConstant.PAYMENT_MANAGER_V2)
//    private PaymentGatewayManager paymentGatewayManager;
//
//    public void fetchTDR(String requestId) {
//        MDC.put(REQUEST_ID, requestId);
//        AnalyticService.update(REQUEST_ID, requestId);
//        int batchSize = 50;
//        int processedCount = 0;
//
//        while (processedCount < batchSize) {
//            Optional<PaymentTDRDetails> optionalTransaction = paymentTDRDetailsRepository.fetchNextTransactionForProcessing();
//            if (!optionalTransaction.isPresent()) {
//                log.info("No pending transactions to process.");
//                break;
//            }
//            PaymentTDRDetails txn = optionalTransaction.get();
//            try {
//                processTransaction(txn);
//                paymentTDRDetailsRepository.save(txn);
//                log.info("Successfully processed txn: {}", txn.getTransactionId());
//                processedCount++;
//            } catch (Exception e) {
//                log.error("Processing failed for txn: {}", txn.getTransactionId(), e);
//            }
//        }
//        diagnosticCheck();
//    }
//
//    private void processTransaction(PaymentTDRDetails transaction) {
//        try {
//            AnalyticService.update(UID, transaction.getUid());
//            AnalyticService.update(PLAN_ID, transaction.getPlanId());
//            AnalyticService.update(REFERENCE_TRANSACTION_ID, transaction.getReferenceId());
//            AnalyticService.update(TRANSACTION_ID, transaction.getTransactionId());
//
//            final BaseTDRResponse tdr = paymentGatewayManager.getTDR(transaction.getTransactionId());
//            if ((tdr.getTdr() != -1) && (tdr.getTdr() != -2)) {
//                AnalyticService.update(TDR, tdr.getTdr());
//            }
//
//            transaction.setIsProcessed(true);
//            transaction.setTdr(tdr.getTdr());
//            transaction.setUpdatedTimestamp(Calendar.getInstance());
//        } catch (Exception e) {
//            log.error("Error processing transaction metrics for: {}", transaction.getTransactionId(), e);
//            throw e;
//        }
//    }
//
//    private void diagnosticCheck() {
//        System.out.println("=== DATABASE DIAGNOSTICS ===");
//
//        // 1. Check total records
//        long total = paymentTDRDetailsRepository.count();
//        System.out.println("Total records: " + total);
//
//        Date now = new Date();
//        // 2. Check eligible records
//        long eligible = paymentTDRDetailsRepository.countEligibleTransactions(now);
//        System.out.println("Eligible unprocessed records: " + eligible);
//
//        // 3. Attempt to fetch with timestamp
////        Instant now = Instant.now();
////        System.out.println("Current timestamp: " + now);
//
//        Optional<PaymentTDRDetails> result = paymentTDRDetailsRepository.fetchNextTransactionForProcessing();
//        System.out.println("Fetched record: " + result.isPresent());
//    }
//}

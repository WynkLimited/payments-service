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
import org.springframework.transaction.annotation.Transactional;
import java.util.Calendar;
import java.util.Optional;
import static in.wynk.common.constant.BaseConstants.PLAN_ID;
import static in.wynk.common.constant.BaseConstants.REFERENCE_TRANSACTION_ID;
import static in.wynk.common.constant.BaseConstants.TRANSACTION_ID;
import static in.wynk.common.constant.BaseConstants.UID;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.PaymentConstants.TDR;

@Service
@Slf4j
public class PaymentTDRManager implements IPaymentTDRManager {

    @Autowired
    private PaymentTDRDetailsDao paymentTDRDetailsRepository;

    @Autowired
    @Qualifier(BeanConstant.PAYMENT_MANAGER_V2)
    private  PaymentGatewayManager paymentGatewayManager;


    @Transactional
    @AnalyseTransaction(name = "tdrDelayEvent")
    public void fetchTDR(String requestId) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        int batchSize = 50; // Process 50 transactions in each cycle
        int processedCount = 0;

        while (processedCount < batchSize) {
            Optional<PaymentTDRDetails> optionalTransaction = paymentTDRDetailsRepository.fetchNextTransactionForProcessing();

            if (optionalTransaction.isPresent()) {
                log.info("No pending transactions to process.");
                break;
            }

            PaymentTDRDetails txn = optionalTransaction.get();

            try {
                processTransaction(txn);
                paymentTDRDetailsRepository.save(txn);
                log.info("Successfully processed txn: {}", txn.getTransactionId());
                processedCount++;

            } catch (Exception e) {
                log.error("Processing failed for txn: " + txn.getTransactionId(), e);
            }
        }
    }

    private void processTransaction(PaymentTDRDetails transaction) {
        AnalyticService.update(UID, transaction.getUid());
        AnalyticService.update(PLAN_ID, transaction.getPlanId());
        AnalyticService.update(REFERENCE_TRANSACTION_ID, transaction.getReference_id());
        AnalyticService.update(REFERENCE_TRANSACTION_ID, transaction.getReference_id());
        AnalyticService.update(TRANSACTION_ID, transaction.getTransactionId());
        final BaseTDRResponse tdr = paymentGatewayManager.getTDR(transaction.getTransactionId());
        if ((tdr.getTdr() != -1) && (tdr.getTdr() != -2)) {
            AnalyticService.update(TDR, tdr.getTdr());
        }
        transaction.setProcessed(true);
        transaction.setTdr(tdr.getTdr());
        transaction.setUpdatedTimestamp(Calendar.getInstance());
    }




}

package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.request.ChargingTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.RenewalChargingTransactionReconciliationStatusRequest;
import in.wynk.payment.gateway.aps.service.ApsCommonGatewayService;
import in.wynk.payment.gateway.payu.service.PayUCommonGateway;
import in.wynk.payment.service.IDataRefreshService;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentGatewayManager;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Nishesh Pandey
 */
@Service
@Slf4j
public class DataRefreshServiceImpl implements IDataRefreshService {
    @Autowired
    private ApsCommonGatewayService apsCommonGatewayService;
    @Autowired
    private PayUCommonGateway payUCommonGateway;
    @Autowired
    private ITransactionManagerService transactionManagerService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IMerchantTransactionService merchantTransactionService;
    @Autowired
    private RetryRegistry retryRegistry;
    @Autowired
    private PaymentGatewayManager paymentGatewayManager;

    @Override
    @TransactionAware(txnId = "#transactionId")
    public void refreshMerchantTableData (String transactionId, String paymentCode) {
        Transaction transaction = transactionManagerService.get(transactionId);
        try {
            if ("aps".equalsIgnoreCase(paymentCode)) {
                apsCommonGatewayService.syncChargingTransactionFromSource(transaction, Optional.empty());
            } else if ("payu".equalsIgnoreCase(paymentCode)) {
                payUCommonGateway.syncChargingTransactionFromSource(transaction);
            }
        } catch (Exception e) {
            log.error("Exception occurred while refreshing merchnat transaction  table with PG data");
        }
    }

    @Override
    public void handleCallback (String paymentCode, String applicationAlias, HttpHeaders headers, String payload) {
        try {
            if ("aps".equalsIgnoreCase(paymentCode) && payload.contains("PAYMENT_STATUS")) {
                ApsCallBackRequestPayload apsCallBackRequestPayload = objectMapper.readValue(payload, ApsCallBackRequestPayload.class);
                ApsChargeStatusResponse[] response = ApsChargeStatusResponse.from(apsCallBackRequestPayload);
                MerchantTransaction merchantTransaction = MerchantTransaction.builder().id(response[0].getOrderId()).response(response).externalTransactionId(response[0].getPgId()).build();
                upsertData(merchantTransaction);
            } else {
                throw new WynkRuntimeException("No support provided for the paymentCode " + paymentCode);
            }
        } catch (Exception ex) {
            throw new WynkRuntimeException("exception occured while updating merchant table for paymentCode " + paymentCode);
        }
    }

    @Override
    @TransactionAware(txnId = "#txnId")
    public void handleCallback (String applicationAlias, HttpHeaders headers, String txnId) {
        Transaction transaction = TransactionContext.get();
        ChargingTransactionReconciliationStatusRequest request;
        if (transaction.getType() == PaymentEvent.RENEW) {
            request = RenewalChargingTransactionReconciliationStatusRequest.builder().transactionId(txnId).planId(transaction.getPlanId()).build();
        } else{
            request = ChargingTransactionReconciliationStatusRequest.builder().transactionId(txnId).planId(transaction.getPlanId()).build();
        }

        paymentGatewayManager.reconcile(request);

    }

    private void upsertData (MerchantTransaction merchantTransaction) {

        MerchantTransaction merchantData = getMerchantData(merchantTransaction.getId());
        if (Objects.nonNull(merchantData)) {
            merchantData.setExternalTokenReferenceId(merchantData.getExternalTokenReferenceId());
            merchantData.setOrderId(merchantData.getOrderId());
            merchantData.setExternalTransactionId(merchantTransaction.getExternalTransactionId());
            merchantData.setRequest(merchantTransaction.getRequest());
            merchantData.setResponse(merchantTransaction.getResponse());
        } else {
            merchantData = merchantTransaction;
        }
        try {
            MerchantTransaction finalMerchantData = merchantData;
            retryRegistry.retry(PaymentConstants.MERCHANT_TRANSACTION_UPSERT_RETRY_KEY).executeRunnable(() -> merchantTransactionService.upsert(finalMerchantData));
        } catch (Exception e) {
            log.error("Unable to refresh data in merchant table");

        }
    }

    private MerchantTransaction getMerchantData (String id) {
        try {
            return merchantTransactionService.getMerchantTransaction(id);
        } catch (Exception e) {
            return null;
        }
    }
}

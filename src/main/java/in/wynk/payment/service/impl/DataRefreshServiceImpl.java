package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.payu.PayUCallbackRequestPayload;
import in.wynk.payment.gateway.aps.service.ApsCommonGatewayService;
import in.wynk.payment.gateway.payu.service.PayUCommonGateway;
import in.wynk.payment.service.IDataRefreshService;
import in.wynk.payment.service.ITransactionManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY041;

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
    private ApplicationEventPublisher eventPublisher;

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
            log.error("Exception occurred while refreshing merchnat transactio  table with PG data");
        }
    }

    @Override
    public void handleCallback (String paymentCode, String applicationAlias, HttpHeaders headers, String payload) {
        if ("aps".equalsIgnoreCase(paymentCode)) {
            ApsCallBackRequestPayload apsCallBackRequestPayload=objectMapper.convertValue(payload, ApsCallBackRequestPayload.class);
            ApsChargeStatusResponse[] response = ApsChargeStatusResponse.from(apsCallBackRequestPayload);
            MerchantTransactionEvent.Builder builder = MerchantTransactionEvent.builder(apsCallBackRequestPayload.getTransactionId());
            builder.response(response[0]);
            builder.externalTransactionId(response[0].getPgId());
            eventPublisher.publishEvent(builder.build());
            return ;
        }
        throw new WynkRuntimeException("No support provided for the paymentCode " +paymentCode);
    }
}
